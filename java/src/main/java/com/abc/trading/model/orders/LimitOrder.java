package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.data.Quantity;

public record LimitOrder(
        String clientOrderId,
        String strategyId,
        String symbol,
        SignalDirection side,
        Quantity quantity,
        double price,
        long timestampNs
) implements Order {
    public LimitOrder {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be positive");
    }

    public LimitOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            int quantity, double price, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, Quantity.fromInt(quantity), price, timestampNs);
    }
}
