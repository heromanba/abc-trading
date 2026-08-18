package com.abc.trading.execution.commands;

import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;

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
        double realizedPnl
) {
    public SubmitOrder {
        if (traderId == null || traderId.isBlank()) throw new IllegalArgumentException("traderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (orderType == null) throw new IllegalArgumentException("orderType is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be finite and positive");
    }

    public LimitOrderIntent toLimitIntent() {
        if (orderType != OrderType.LIMIT) throw new IllegalStateException("order is not limit type");
        return new LimitOrderIntent(strategyId, symbol, inputSequence, timestampNs, correlationId,
                clientOrderId, side, quantity, price, currentPosition, realizedPnl);
    }

    public OrderIntent toMarketIntent() {
        if (orderType != OrderType.MARKET) throw new IllegalStateException("order is not market type");
        return new OrderIntent(strategyId, symbol, inputSequence, timestampNs, correlationId,
                clientOrderId, side, quantity, price, currentPosition, realizedPnl);
    }
}