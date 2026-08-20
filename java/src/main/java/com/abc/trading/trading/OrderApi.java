package com.abc.trading.trading;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TimeInForce;
import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;
import com.abc.trading.execution.commands.OrderType;
import com.abc.trading.execution.commands.SubmitOrder;
import com.abc.trading.common.factories.OrderFactory;
import com.abc.trading.model.orders.Order;
import com.abc.trading.model.orders.MarketOrder;
import com.abc.trading.msgbus.MessageBus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderApi {
    private final MessageBus bus;
    private final String strategyId;
    private final StrategyContext context;
    private final OrderFactory orderFactory;
    private final Map<String, String> orderSymbols = new LinkedHashMap<>();

    OrderApi(MessageBus bus, String strategyId, StrategyContext context) {
        this.bus = bus;
        this.strategyId = strategyId;
        this.context = context;
        this.orderFactory = new OrderFactory("BACKTEST", strategyId);
    }

    public String limit(String symbol, SignalDirection side, int quantity, double limitPrice) {
        return limit(symbol, side, quantity, limitPrice, TimeInForce.GTC, 0L);
    }

    public String limit(String symbol, SignalDirection side, int quantity, double limitPrice,
            TimeInForce timeInForce, long expireTimeNs) {
        SubmitOrder order = createSubmitOrder(orderFactory.limit(
                symbol, side, quantity, limitPrice, context.marketTimestamp()), timeInForce, expireTimeNs);
        bus.publish(order);
        return order.clientOrderId();
    }

    public String market(String symbol, SignalDirection side, int quantity, double price) {
        return market(symbol, side, quantity, price, TimeInForce.GTC, 0L);
    }

    public String market(String symbol, SignalDirection side, int quantity, double price,
            TimeInForce timeInForce, long expireTimeNs) {
        SubmitOrder order = createSubmitOrder(orderFactory.market(
            symbol, side, quantity, price, context.marketTimestamp()), timeInForce, expireTimeNs);
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
        return order.clientOrderId();
    }

    public void cancel(String clientOrderId) {
        String symbol = orderSymbols.get(clientOrderId);
        if (symbol == null) throw new IllegalArgumentException("Unknown client order: " + clientOrderId);
        bus.publish(new CancelOrder(strategyId, symbol, clientOrderId,
                strategyId + "-cancel-" + context.sequence(), context.marketTimestamp()));
    }

    public void modify(String clientOrderId, Integer quantity, Double price) {
        String symbol = orderSymbols.get(clientOrderId);
        if (symbol == null) throw new IllegalArgumentException("Unknown client order: " + clientOrderId);
        bus.publish(new ModifyOrder(strategyId, symbol, clientOrderId,
                strategyId + "-modify-" + context.sequence(), context.marketTimestamp(), quantity, price));
    }

    private SubmitOrder createSubmitOrder(Order order) {
        return createSubmitOrder(order, TimeInForce.GTC, 0L);
    }

    private SubmitOrder createSubmitOrder(Order order, TimeInForce timeInForce, long expireTimeNs) {
        int position = context.position(order.symbol());
        String correlationId = order.symbol() + "-" + context.marketTimestamp() + "-" + context.sequence();
        SubmitOrder submitOrder = new SubmitOrder(
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
                order,
                timeInForce,
                expireTimeNs);
            orderSymbols.put(submitOrder.clientOrderId(), submitOrder.symbol());
            return submitOrder;
    }

    private static int targetPosition(int position, SignalDirection side, int quantity) {
        return side == SignalDirection.BUY ? position + quantity : position - quantity;
    }
}
