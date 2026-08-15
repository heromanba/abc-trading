package com.abc.trading.execution;

import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.portfolio.Portfolio;
import com.abc.trading.risk.RiskEngine;

/** Minimal deterministic execution boundary composed through typed endpoints. */
public final class ExecutionEngine {
    public static final String RISK_ENDPOINT = "RiskEngine.execute";
    public static final String EXECUTION_ENDPOINT = "ExecEngine.execute";

    private final MessageBus bus;
    private final RiskEngine riskEngine;

    public ExecutionEngine(MessageBus bus, RiskEngine riskEngine, Portfolio portfolio) {
        this.bus = bus;
        this.riskEngine = riskEngine;
        bus.subscribe(OrderIntent.class, this::submit);
        bus.registerEndpoint(RISK_ENDPOINT, OrderIntent.class, this::onRiskCommand);
        bus.registerEndpoint(EXECUTION_ENDPOINT, OrderIntent.class, order -> {
            bus.publish(new OrderAccepted(order));
            OrderFill fill = new OrderFill(
                order.strategyId(),
                order.symbol(),
                order.inputSequence(),
                order.marketTimestamp(),
                order.correlationId(),
                order.orderId(),
                order.side(),
                order.quantity(),
                order.price(),
                order.currentPosition(),
                order.realizedPnl());
            var positionUpdate = portfolio.applyFill(fill);
            bus.publish(fill.withState(positionUpdate.position(), positionUpdate.realizedPnl()));
            bus.publish(positionUpdate);
        });
        bus.subscribe(OrderAccepted.class, accepted -> portfolio.applyOrderIntent(accepted.order()));
    }

    public void submit(OrderIntent order) {
        bus.send(RISK_ENDPOINT, OrderIntent.class, order);
    }

    private void onRiskCommand(OrderIntent order) {
        if (riskEngine.evaluate(order).approved()) {
            bus.send(EXECUTION_ENDPOINT, OrderIntent.class, order);
        }
    }
}