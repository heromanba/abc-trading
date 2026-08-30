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
import com.abc.trading.model.orders.StopLimitOrder;
import com.abc.trading.model.orders.StopMarketOrder;
import com.abc.trading.model.orders.TrailingStopMarketOrder;
import com.abc.trading.model.orders.TrailingStopLimitOrder;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.data.Quantity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;

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

        public String emulatedMarket(String symbol, SignalDirection side, int quantity, double price,
            TriggerType emulationTrigger) {
        SubmitOrder order = createSubmitOrder(orderFactory.market(
            symbol, side, quantity, price, context.marketTimestamp()), TimeInForce.GTC, 0L,
            emulationTrigger);
        bus.publish(order);
        return order.clientOrderId();
        }

        public String emulatedLimit(String symbol, SignalDirection side, int quantity, double limitPrice,
            TriggerType emulationTrigger) {
        SubmitOrder order = createSubmitOrder(orderFactory.limit(
            symbol, side, quantity, limitPrice, context.marketTimestamp()), TimeInForce.GTC, 0L,
            emulationTrigger);
        bus.publish(order);
        return order.clientOrderId();
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

    public String stopMarket(String symbol, SignalDirection side, int quantity, double triggerPrice) {
        return stopMarket(symbol, side, quantity, triggerPrice, TimeInForce.GTC, 0L);
    }

    public String stopMarket(String symbol, SignalDirection side, int quantity, double triggerPrice,
            TimeInForce timeInForce, long expireTimeNs) {
        SubmitOrder order = createSubmitOrder(orderFactory.stopMarket(
                symbol, side, quantity, triggerPrice, context.marketTimestamp()), timeInForce, expireTimeNs);
        bus.publish(order);
        return order.clientOrderId();
    }

    public String stopLimit(String symbol, SignalDirection side, int quantity, double limitPrice,
            double triggerPrice) {
        return stopLimit(symbol, side, quantity, limitPrice, triggerPrice, TimeInForce.GTC, 0L);
    }

    public String stopLimit(String symbol, SignalDirection side, int quantity, double limitPrice,
            double triggerPrice, TimeInForce timeInForce, long expireTimeNs) {
        SubmitOrder order = createSubmitOrder(orderFactory.stopLimit(
                symbol, side, quantity, limitPrice, triggerPrice, context.marketTimestamp()), timeInForce, expireTimeNs);
        bus.publish(order);
        return order.clientOrderId();
    }

    public String trailingStopMarket(String symbol, SignalDirection side, int quantity,
            double activationPrice, double trailingOffset, TrailingOffsetType offsetType,
            TriggerType triggerType, TimeInForce timeInForce, long expireTimeNs) {
        SubmitOrder order = createSubmitOrder(orderFactory.trailingStopMarket(symbol, side, quantity,
                activationPrice, 0.0, triggerType, trailingOffset, offsetType, context.marketTimestamp()),
                timeInForce, expireTimeNs);
        bus.publish(order);
        return order.clientOrderId();
    }

    public String trailingStopLimit(String symbol, SignalDirection side, int quantity,
            double limitPrice, double activationPrice, double limitOffset, double trailingOffset,
            TrailingOffsetType offsetType, TriggerType triggerType, TimeInForce timeInForce,
            long expireTimeNs) {
        SubmitOrder order = createSubmitOrder(orderFactory.trailingStopLimit(symbol, side, quantity,
                limitPrice, activationPrice, 0.0, triggerType, limitOffset, trailingOffset,
                offsetType, context.marketTimestamp()), timeInForce, expireTimeNs);
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
        modify(clientOrderId, quantity, price, null);
    }

    public void modify(String clientOrderId, Integer quantity, Double price, Double triggerPrice) {
        String symbol = orderSymbols.get(clientOrderId);
        if (symbol == null) throw new IllegalArgumentException("Unknown client order: " + clientOrderId);
        bus.publish(new ModifyOrder(strategyId, symbol, clientOrderId,
                strategyId + "-modify-" + context.sequence(), context.marketTimestamp(), quantity, price, triggerPrice));
    }

    private SubmitOrder createSubmitOrder(Order order, TimeInForce timeInForce, long expireTimeNs) {
        return createSubmitOrder(order, timeInForce, expireTimeNs, TriggerType.NO_TRIGGER);
        }

        private SubmitOrder createSubmitOrder(Order order, TimeInForce timeInForce, long expireTimeNs,
            TriggerType emulationTrigger) {
        BigDecimal position = context.position(order.symbol());
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
                orderType(order),
                Quantity.fromInt(order.quantity()),
                order.price(),
                targetPosition(position, order.side(), order.quantity()),
                0.0,
                order,
                timeInForce,
                expireTimeNs,
                order.triggerPrice(),
                order.triggerType(),
                order.activationPrice(),
                order.trailingOffset(),
                order.trailingOffsetType(),
                order.limitOffset(),
                emulationTrigger);
            orderSymbols.put(submitOrder.clientOrderId(), submitOrder.symbol());
            return submitOrder;
    }

    private static BigDecimal targetPosition(BigDecimal position, SignalDirection side, int quantity) {
        BigDecimal signedQuantity = BigDecimal.valueOf(quantity);
        return side == SignalDirection.BUY ? position.add(signedQuantity) : position.subtract(signedQuantity);
    }

    private static OrderType orderType(Order order) {
        if (order instanceof StopMarketOrder) return OrderType.STOP_MARKET;
        if (order instanceof StopLimitOrder) return OrderType.STOP_LIMIT;
        if (order instanceof TrailingStopMarketOrder) return OrderType.TRAILING_STOP_MARKET;
        if (order instanceof TrailingStopLimitOrder) return OrderType.TRAILING_STOP_LIMIT;
        if (order instanceof MarketOrder) return OrderType.MARKET;
        return OrderType.LIMIT;
    }
}
