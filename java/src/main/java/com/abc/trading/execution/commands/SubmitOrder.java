package com.abc.trading.execution.commands;

import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.model.orders.Order;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TimeInForce;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.execution.TrailingOffsetType;

/** Trading command analogous to Nautilus SubmitOrder. */
public record SubmitOrder(
        String traderId,
        String strategyId,
        String symbol,
        long inputSequence,
        long timestampNs,
        String clientOrderId,
        String commandId,
        String correlationId,
        SignalDirection side,
        OrderType orderType,
        int quantity,
        double price,
        int currentPosition,
        double realizedPnl,
        Order order,
        TimeInForce timeInForce,
        long expireTimeNs,
        double triggerPrice,
        TriggerType triggerType,
        double activationPrice,
        double trailingOffset,
        TrailingOffsetType trailingOffsetType,
        double limitOffset,
        TriggerType emulationTrigger
) {
        public SubmitOrder(String traderId, String strategyId, String symbol, long inputSequence,
            long timestampNs, String clientOrderId, String commandId, String correlationId,
            SignalDirection side, OrderType orderType, int quantity, double price,
            int currentPosition, double realizedPnl, Order order) {
        this(traderId, strategyId, symbol, inputSequence, timestampNs, clientOrderId, commandId,
            correlationId, side, orderType, quantity, price, currentPosition, realizedPnl,
            order, TimeInForce.GTC, 0L, order.triggerPrice(), order.triggerType(),
            order.activationPrice(), order.trailingOffset(), order.trailingOffsetType(), order.limitOffset(),
            order.emulationTrigger());
        }
    public SubmitOrder {
        if (traderId == null || traderId.isBlank()) throw new IllegalArgumentException("traderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (orderType == null) throw new IllegalArgumentException("orderType is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        boolean trailingOrder = orderType == OrderType.TRAILING_STOP_MARKET || orderType == OrderType.TRAILING_STOP_LIMIT;
        if (!Double.isFinite(price) || (!trailingOrder && price <= 0.0)) {
            throw new IllegalArgumentException("price must be finite and positive");
        }
        if (order == null) throw new IllegalArgumentException("order is required");
        if (timeInForce == null) throw new IllegalArgumentException("timeInForce is required");
        if (timeInForce == TimeInForce.GTD && expireTimeNs <= timestampNs) {
            throw new IllegalArgumentException("GTD expireTimeNs must be after timestampNs");
        }
        boolean stopOrder = orderType == OrderType.STOP_MARKET || orderType == OrderType.STOP_LIMIT
                || orderType == OrderType.TRAILING_STOP_MARKET || orderType == OrderType.TRAILING_STOP_LIMIT;
        if (stopOrder && !trailingOrder && (!Double.isFinite(triggerPrice) || triggerPrice <= 0.0)) {
            throw new IllegalArgumentException("stop order triggerPrice must be positive");
        }
        if (trailingOrder && (!Double.isFinite(trailingOffset) || trailingOffset <= 0.0
                || trailingOffsetType == null)) {
            throw new IllegalArgumentException("supported trailing offset is required");
        }
        if (!stopOrder && triggerType != TriggerType.NO_TRIGGER) {
            throw new IllegalArgumentException("triggerType is only valid for stop orders");
        }
        if (stopOrder && (triggerType == null || triggerType == TriggerType.NO_TRIGGER)) {
            throw new IllegalArgumentException("stop order triggerType is required");
        }
        if (emulationTrigger == null) throw new IllegalArgumentException("emulationTrigger is required");
        if (emulationTrigger != TriggerType.NO_TRIGGER
                && emulationTrigger != TriggerType.DEFAULT
                && emulationTrigger != TriggerType.BID_ASK
                && emulationTrigger != TriggerType.LAST_PRICE) {
            throw new IllegalArgumentException("unsupported emulationTrigger");
        }
        if (!order.clientOrderId().equals(clientOrderId)) throw new IllegalArgumentException("order/clientOrderId mismatch");
        if (!order.strategyId().equals(strategyId)) throw new IllegalArgumentException("order/strategyId mismatch");
    }

    public LimitOrderIntent toLimitIntent() {
        if (orderType != OrderType.LIMIT && orderType != OrderType.STOP_LIMIT
            && orderType != OrderType.TRAILING_STOP_LIMIT) throw new IllegalStateException("order is not limit type");
        return new LimitOrderIntent(strategyId, symbol, inputSequence, timestampNs, correlationId,
            clientOrderId, side, quantity, price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
            triggerPrice, triggerType, activationPrice, trailingOffset, trailingOffsetType, limitOffset);
    }

    public OrderIntent toMarketIntent() {
        if (orderType != OrderType.MARKET && orderType != OrderType.STOP_MARKET
            && orderType != OrderType.TRAILING_STOP_MARKET) throw new IllegalStateException("order is not market type");
        return new OrderIntent(strategyId, symbol, inputSequence, timestampNs, correlationId,
            clientOrderId, side, quantity, price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
            triggerPrice, triggerType, activationPrice, trailingOffset, trailingOffsetType);
    }
}