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

    public ExecutionEngine(MessageBus bus, RiskEngine riskEngine, Portfolio portfolio, Cache cache) {
        this.bus = bus;
        this.riskEngine = riskEngine;
        this.cache = cache;
        bus.subscribe(SubmitOrder.class, this::submit);
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
    }

    public void registerClient(ExecutionClient client) {
        if (clients.putIfAbsent(client.venue(), client) != null) {
            throw new IllegalArgumentException("Execution client already registered for " + client.venue().value());
        }
    }

    public void submit(SubmitOrder command) {
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
        stateMachine.submit(order.orderId());
        RiskDecision decision = riskEngine.evaluate(order);
        if (decision.approved()) {
            stateMachine.accept(order.orderId());
            bus.send(EXECUTION_ENDPOINT, OrderIntent.class, order);
        } else {
            stateMachine.deny(order.orderId());
            bus.publish(new OrderDenied(order, decision.reason()));
        }
    }

    private void onLimitRiskCommand(LimitOrderIntent order) {
        stateMachine.initialize(order.orderId(), order.quantity(), order.timeInForce(), order.expireTimeNs());
        stateMachine.submit(order.orderId());
        RiskDecision decision = riskEngine.evaluate(order);
        if (decision.approved()) {
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

    private ExecutionClient clientFor(String symbol) {
        String venue = cache.venue(symbol);
        ExecutionClient client = clients.get(new VenueId(venue));
        if (client == null) throw new IllegalStateException("No execution client for " + venue);
        return client;
    }
}