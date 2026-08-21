package com.abc.trading.execution;

import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.cache.Cache;
import com.abc.trading.portfolio.Portfolio;
import com.abc.trading.portfolio.PositionUpdate;
import com.abc.trading.risk.RiskEngine;
import com.abc.trading.risk.RiskDecision;
import com.abc.trading.execution.commands.OrderType;
import com.abc.trading.execution.commands.SubmitOrder;
import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;
import com.abc.trading.data.MarketDataSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal deterministic execution boundary composed through typed endpoints. */
public final class ExecutionEngine {
    public static final String RISK_ENDPOINT = "RiskEngine.execute";
    public static final String EXECUTION_ENDPOINT = "ExecEngine.execute";

    private final MessageBus bus;
    private final RiskEngine riskEngine;
    private final Cache cache;
    private final Map<VenueId, ExecutionClient> clients = new LinkedHashMap<>();
    private final OrderStateMachine stateMachine = new OrderStateMachine();
    private final OrderEmulator orderEmulator;

    public ExecutionEngine(MessageBus bus, RiskEngine riskEngine, Portfolio portfolio, Cache cache) {
        this.bus = bus;
        this.riskEngine = riskEngine;
        this.cache = cache;
        this.orderEmulator = new OrderEmulator(this::releaseEmulated);
        bus.subscribe(SubmitOrder.class, this::submit);
        bus.subscribe(MarketDataSnapshot.class, orderEmulator::processMarketData);
        bus.subscribe(CancelOrder.class, this::cancel);
        bus.subscribe(ModifyOrder.class, this::modify);
        bus.subscribe(OrderIntent.class, this::submit);
        bus.registerEndpoint(RISK_ENDPOINT, OrderIntent.class, this::onRiskCommand);
        bus.registerEndpoint(EXECUTION_ENDPOINT, OrderIntent.class, order -> {
            bus.publish(new OrderAccepted(order));
            try {
                clientFor(order).submitMarketOrder(order);
            } catch (RuntimeException error) {
                stateMachine.reject(order.orderId());
                bus.publish(new OrderRejected(order, error.getMessage()));
            }
        });
        bus.subscribe(LimitOrderIntent.class, this::submitLimit);
        bus.registerEndpoint("RiskEngine.execute_limit", LimitOrderIntent.class, this::onLimitRiskCommand);
        bus.registerEndpoint("ExecEngine.execute_limit", LimitOrderIntent.class, order -> {
            bus.publish(new LimitOrderAccepted(order));
            try {
                clientFor(order).submitLimitOrder(order);
            } catch (RuntimeException error) {
                stateMachine.reject(order.orderId());
                bus.publish(new LimitOrderRejected(order, error.getMessage()));
            }
        });
        bus.subscribe(OrderAccepted.class, accepted -> portfolio.applyOrderIntent(accepted.order()));
        bus.subscribe(OrderFill.class, fill -> {
            stateMachine.fill(fill.orderId(), fill.quantity(), fill.price());
            PositionUpdate positionUpdate = portfolio.applyFill(fill);
            bus.publish(new SettledOrderFill(fill, positionUpdate.position(), positionUpdate.realizedPnl()));
            bus.publish(positionUpdate);
        });
        bus.subscribe(OrderExpired.class, this::expire);
        bus.subscribe(OrderCanceled.class, this::venueCanceled);
        bus.subscribe(OrderTriggered.class, event -> stateMachine.trigger(event.orderId()));
    }

    public void registerClient(ExecutionClient client) {
        if (clients.putIfAbsent(client.venue(), client) != null) {
            throw new IllegalArgumentException("Execution client already registered for " + client.venue().value());
        }
    }

    public void submit(SubmitOrder command) {
        if (command.emulationTrigger() != TriggerType.NO_TRIGGER) {
            stateMachine.initialize(command.clientOrderId(), command.quantity(), command.timeInForce(), command.expireTimeNs());
            stateMachine.emulate(command.clientOrderId());
            bus.publish(new OrderEmulated(command.clientOrderId()));
            orderEmulator.cacheSubmitOrder(command);
            return;
        }
        if (command.orderType() == OrderType.MARKET) {
            bus.publish(command.toMarketIntent());
        } else {
            bus.publish(command.toLimitIntent());
        }
    }

    public void submit(OrderIntent order) {
        bus.send(RISK_ENDPOINT, OrderIntent.class, order);
    }

    public void submitLimit(LimitOrderIntent order) {
        bus.send("RiskEngine.execute_limit", LimitOrderIntent.class, order);
    }

    private ExecutionClient clientFor(OrderIntent order) {
        String venue = cache.venue(order.symbol());
        ExecutionClient client = clients.get(new VenueId(venue));
        if (client == null) throw new IllegalStateException("No execution client for " + venue);
        return client;
    }

    private ExecutionClient clientFor(LimitOrderIntent order) {
        String venue = cache.venue(order.symbol());
        ExecutionClient client = clients.get(new VenueId(venue));
        if (client == null) throw new IllegalStateException("No execution client for " + venue);
        return client;
    }

    private void onRiskCommand(OrderIntent order) {
        stateMachine.initialize(order.orderId(), order.quantity(), order.timeInForce(), order.expireTimeNs());
        RiskDecision decision = riskEngine.evaluate(order);
        if (decision.approved()) {
            stateMachine.submit(order.orderId());
            stateMachine.accept(order.orderId());
            bus.send(EXECUTION_ENDPOINT, OrderIntent.class, order);
        } else {
            stateMachine.deny(order.orderId());
            bus.publish(new OrderDenied(order, decision.reason()));
        }
    }

    private void onLimitRiskCommand(LimitOrderIntent order) {
        stateMachine.initialize(order.orderId(), order.quantity(), order.timeInForce(), order.expireTimeNs());
        RiskDecision decision = riskEngine.evaluate(order);
        if (decision.approved()) {
            stateMachine.submit(order.orderId());
            stateMachine.accept(order.orderId());
            bus.send("ExecEngine.execute_limit", LimitOrderIntent.class, order);
        } else {
            stateMachine.deny(order.orderId());
            bus.publish(new LimitOrderDenied(order, decision.reason()));
        }
    }

    public void cancel(CancelOrder command) {
        try {
            OrderState current = stateMachine.state(command.clientOrderId());
            if (current.status() == OrderStatus.EMULATED || current.status() == OrderStatus.RELEASED) {
                if (orderEmulator.cancel(command.clientOrderId())) {
                    stateMachine.cancel(command.clientOrderId());
                    bus.publish(new OrderCanceled(command));
                    return;
                }
                throw new IllegalStateException("order is not emulated");
            }
            if (!current.status().isOpen()) throw new IllegalStateException("order is not open");
            stateMachine.pendingCancel(command.clientOrderId());
            if (clientFor(command.symbol()).cancelOrder(command)) {
                stateMachine.cancel(command.clientOrderId());
                bus.publish(new OrderCanceled(command));
            } else {
                stateMachine.cancelReject(command.clientOrderId());
                bus.publish(new OrderCancelRejected(command, "order is not working"));
            }
        } catch (RuntimeException error) {
            try {
                if (stateMachine.state(command.clientOrderId()).status() == OrderStatus.PENDING_CANCEL) {
                    stateMachine.cancelReject(command.clientOrderId());
                }
            } catch (IllegalArgumentException ignored) { }
            bus.publish(new OrderCancelRejected(command, error.getMessage()));
        }
    }

    public void modify(ModifyOrder command) {
        try {
            OrderState current = stateMachine.state(command.clientOrderId());
            if (!current.status().isOpen()) throw new IllegalStateException("order is not open");
            stateMachine.pendingUpdate(command.clientOrderId());
            if (clientFor(command.symbol()).modifyOrder(command)) {
                int quantity = command.quantity() == null ? current.submittedQuantity() : command.quantity();
                stateMachine.update(command.clientOrderId(), quantity);
                bus.publish(new OrderModified(command));
            } else {
                stateMachine.updateReject(command.clientOrderId());
                bus.publish(new OrderModifyRejected(command, "order cannot be modified"));
            }
        } catch (RuntimeException error) {
            try {
                if (stateMachine.state(command.clientOrderId()).status() == OrderStatus.PENDING_UPDATE) {
                    stateMachine.updateReject(command.clientOrderId());
                }
            } catch (IllegalArgumentException ignored) { }
            bus.publish(new OrderModifyRejected(command, error.getMessage()));
        }
    }

    private void expire(OrderExpired event) {
        OrderState state = stateMachine.state(event.orderId());
        if (state.status().isOpen()) stateMachine.expire(event.orderId());
    }

    private void venueCanceled(OrderCanceled event) {
        OrderState state = stateMachine.state(event.command().clientOrderId());
        if (state.status().isOpen()) stateMachine.cancel(event.command().clientOrderId());
    }

    public OrderState orderState(String orderId) {
        return stateMachine.state(orderId);
    }

    public Map<String, OrderState> orderStates() {
        return stateMachine.states();
    }

    public OrderEmulator orderEmulator() {
        return orderEmulator;
    }

    private void releaseEmulated(SubmitOrder command) {
        stateMachine.release(command.clientOrderId());
        bus.publish(new OrderReleased(command.clientOrderId()));
        stateMachine.submit(command.clientOrderId());
        RiskDecision decision = command.orderType() == OrderType.LIMIT
                || command.orderType() == OrderType.STOP_LIMIT
                || command.orderType() == OrderType.TRAILING_STOP_LIMIT
                ? riskEngine.evaluate(command.toLimitIntent())
                : riskEngine.evaluate(command.toMarketIntent());
        if (!decision.approved()) {
            stateMachine.deny(command.clientOrderId());
            if (command.orderType() == OrderType.LIMIT || command.orderType() == OrderType.STOP_LIMIT
                    || command.orderType() == OrderType.TRAILING_STOP_LIMIT) {
                bus.publish(new LimitOrderDenied(command.toLimitIntent(), decision.reason()));
            } else {
                bus.publish(new OrderDenied(command.toMarketIntent(), decision.reason()));
            }
            return;
        }
        stateMachine.accept(command.clientOrderId());
        if (command.orderType() == OrderType.LIMIT || command.orderType() == OrderType.STOP_LIMIT
                || command.orderType() == OrderType.TRAILING_STOP_LIMIT) {
            bus.send("ExecEngine.execute_limit", LimitOrderIntent.class, command.toLimitIntent());
        } else {
            bus.send(EXECUTION_ENDPOINT, OrderIntent.class, command.toMarketIntent());
        }
    }

    public OrderState emulateOrder(String orderId) {
        OrderState state = stateMachine.emulate(orderId);
        bus.publish(new OrderEmulated(orderId));
        return state;
    }

    public OrderState releaseOrder(String orderId) {
        OrderState state = stateMachine.release(orderId);
        bus.publish(new OrderReleased(orderId));
        return state;
    }

    public OrderState submitReleasedOrder(String orderId) {
        return stateMachine.submit(orderId);
    }

    public OrderState triggerOrder(String orderId) {
        OrderState state = stateMachine.trigger(orderId);
        bus.publish(new OrderTriggered(orderId));
        return state;
    }

    public OrderState voidOrder(String orderId) {
        OrderState state = stateMachine.voidOrder(orderId);
        bus.publish(new OrderVoided(orderId));
        return state;
    }

    private ExecutionClient clientFor(String symbol) {
        String venue = cache.venue(symbol);
        ExecutionClient client = clients.get(new VenueId(venue));
        if (client == null) throw new IllegalStateException("No execution client for " + venue);
        return client;
    }
}