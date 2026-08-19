package com.abc.trading.trading;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.commands.OrderType;
import com.abc.trading.execution.commands.SubmitOrder;
import com.abc.trading.common.factories.OrderFactory;
import com.abc.trading.model.orders.Order;
import com.abc.trading.model.orders.MarketOrder;
import com.abc.trading.msgbus.MessageBus;

public final class OrderApi {
    private final MessageBus bus;
    private final String strategyId;
    private final StrategyContext context;
    private final OrderFactory orderFactory;

    OrderApi(MessageBus bus, String strategyId, StrategyContext context) {
        this.bus = bus;
        this.strategyId = strategyId;
        this.context = context;
        this.orderFactory = new OrderFactory("BACKTEST", strategyId);
    }

    public void limit(String symbol, SignalDirection side, int quantity, double limitPrice) {
        bus.publish(createSubmitOrder(orderFactory.limit(
                symbol, side, quantity, limitPrice, context.marketTimestamp())));
    }

    public void market(String symbol, SignalDirection side, int quantity, double price) {
        SubmitOrder order = createSubmitOrder(orderFactory.market(
            symbol, side, quantity, price, context.marketTimestamp()));
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

    private SubmitOrder createSubmitOrder(Order order) {
        int position = context.position(order.symbol());
        String correlationId = order.symbol() + "-" + context.marketTimestamp() + "-" + context.sequence();
        return new SubmitOrder(
                orderFactory.traderId(),
                orderFactory.strategyId(),
                order.symbol(),
                context.inputSequence(),
                context.marketTimestamp(),
                order.clientOrderId(),
                    correlationId,
                correlationId,
                order.side(),
                order instanceof MarketOrder ? OrderType.MARKET : OrderType.LIMIT,
                order.quantity(),
                order.price(),
                targetPosition(position, order.side(), order.quantity()),
                0.0,
                order);
    }

    private static int targetPosition(int position, SignalDirection side, int quantity) {
        return side == SignalDirection.BUY ? position + quantity : position - quantity;
    }
}
