package com.abc.trading.trading;

import com.abc.trading.execution.DeterministicOrderId;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.msgbus.MessageBus;

public final class OrderApi {
    private final MessageBus bus;
    private final String strategyId;
    private final StrategyContext context;

    OrderApi(MessageBus bus, String strategyId, StrategyContext context) {
        this.bus = bus;
        this.strategyId = strategyId;
        this.context = context;
    }

    public void market(String symbol, SignalDirection side, int quantity, double price) {
        submitMarket(symbol, side, quantity, price);
    }

    public void limit(String symbol, SignalDirection side, int quantity, double limitPrice) {
        int position = context.position(symbol);
        String correlationId = symbol + "-limit-" + context.marketTimestamp() + "-" + context.sequence();
        bus.publish(new LimitOrderIntent(
                strategyId,
                symbol,
                context.inputSequence(),
                context.marketTimestamp(),
                correlationId,
                DeterministicOrderId.fromCorrelation(correlationId),
                side,
                quantity,
                limitPrice,
                targetPosition(position, side, quantity),
                0.0));
    }

    private void submitMarket(String symbol, SignalDirection side, int quantity, double price) {
        int position = context.position(symbol);
        String correlationId = symbol + "-" + context.marketTimestamp() + "-" + context.sequence();
        bus.publish(new StrategySignal(
                strategyId,
                symbol,
                context.inputSequence(),
                context.marketTimestamp(),
                correlationId,
                side,
                price,
                position));
        bus.publish(new OrderIntent(
                strategyId,
                symbol,
                context.inputSequence(),
                context.marketTimestamp(),
                correlationId,
                DeterministicOrderId.fromCorrelation(correlationId),
                side,
                quantity,
                price,
                targetPosition(position, side, quantity),
                0.0));
    }

    private static int targetPosition(int position, SignalDirection side, int quantity) {
        return side == SignalDirection.BUY ? position + quantity : position - quantity;
    }
}
