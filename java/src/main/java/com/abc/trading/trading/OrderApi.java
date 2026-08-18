package com.abc.trading.trading;

import com.abc.trading.execution.DeterministicOrderId;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.commands.OrderType;
import com.abc.trading.execution.commands.SubmitOrder;
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

    public void limit(String symbol, SignalDirection side, int quantity, double limitPrice) {
        bus.publish(createSubmitOrder(symbol, side, quantity, limitPrice, OrderType.LIMIT));
    }

        public void market(String symbol, SignalDirection side, int quantity, double price) {
        SubmitOrder order = createSubmitOrder(symbol, side, quantity, price, OrderType.MARKET);
        bus.publish(new StrategySignal(
            strategyId,
            symbol,
            context.inputSequence(),
            context.marketTimestamp(),
            order.correlationId(),
            side,
            price,
            context.position(symbol)));
        bus.publish(order);
        }

        private SubmitOrder createSubmitOrder(
            String symbol,
            SignalDirection side,
            int quantity,
            double price,
            OrderType orderType) {
        int position = context.position(symbol);
        String correlationId = symbol + "-" + context.marketTimestamp() + "-" + context.sequence();
        return new SubmitOrder(
            "BACKTEST",
            strategyId,
                symbol,
                context.inputSequence(),
                context.marketTimestamp(),
                correlationId,
                DeterministicOrderId.fromCorrelation(correlationId),
                correlationId,
                side,
            orderType,
                quantity,
                price,
                targetPosition(position, side, quantity),
            0.0);
    }

    private static int targetPosition(int position, SignalDirection side, int quantity) {
        return side == SignalDirection.BUY ? position + quantity : position - quantity;
    }
}
