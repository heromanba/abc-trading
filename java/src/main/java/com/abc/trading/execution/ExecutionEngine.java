package com.abc.trading.execution;

import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.cache.Cache;
import com.abc.trading.portfolio.Portfolio;
import com.abc.trading.portfolio.PositionUpdate;
import com.abc.trading.risk.RiskEngine;
import com.abc.trading.risk.RiskDecision;

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

    public ExecutionEngine(MessageBus bus, RiskEngine riskEngine, Portfolio portfolio, Cache cache) {
        this.bus = bus;
        this.riskEngine = riskEngine;
        this.cache = cache;
        bus.subscribe(OrderIntent.class, this::submit);
        bus.registerEndpoint(RISK_ENDPOINT, OrderIntent.class, this::onRiskCommand);
        bus.registerEndpoint(EXECUTION_ENDPOINT, OrderIntent.class, order -> {
            bus.publish(new OrderAccepted(order));
            ExecutionClient client = clientFor(order);
            OrderFill fill = client.submitMarketOrder(order);
            PositionUpdate positionUpdate = portfolio.applyFill(fill);
            bus.publish(fill.withState(positionUpdate.position(), positionUpdate.realizedPnl()));
            bus.publish(positionUpdate);
        });
        bus.subscribe(LimitOrderIntent.class, this::submitLimit);
        bus.registerEndpoint("RiskEngine.execute_limit", LimitOrderIntent.class, this::onLimitRiskCommand);
        bus.registerEndpoint("ExecEngine.execute_limit", LimitOrderIntent.class, order -> {
            bus.publish(new LimitOrderAccepted(order));
            clientFor(order).submitLimitOrder(order);
        });
        bus.subscribe(OrderAccepted.class, accepted -> portfolio.applyOrderIntent(accepted.order()));
    }

    public void submit(OrderIntent order) {
        bus.send(RISK_ENDPOINT, OrderIntent.class, order);
    }

    public void submitLimit(LimitOrderIntent order) {
        bus.send("RiskEngine.execute_limit", LimitOrderIntent.class, order);
    }

    public void registerClient(ExecutionClient client) {
        if (clients.putIfAbsent(client.venue(), client) != null) {
            throw new IllegalArgumentException("Execution client already registered for " + client.venue().value());
        }
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
        RiskDecision decision = riskEngine.evaluate(order);
        if (decision.approved()) {
            bus.send(EXECUTION_ENDPOINT, OrderIntent.class, order);
        } else {
            bus.publish(new OrderDenied(order, decision.reason()));
        }
    }

    private void onLimitRiskCommand(LimitOrderIntent order) {
        RiskDecision decision = riskEngine.evaluate(order);
        if (decision.approved()) {
            bus.send("ExecEngine.execute_limit", LimitOrderIntent.class, order);
        } else {
            bus.publish(new LimitOrderDenied(order, decision.reason()));
        }
    }
}