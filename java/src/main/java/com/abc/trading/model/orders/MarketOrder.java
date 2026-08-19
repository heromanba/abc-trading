package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;

public record MarketOrder(
        String clientOrderId,
        String strategyId,
        String symbol,
        SignalDirection side,
        int quantity,
        double price,
        long timestampNs
) implements Order {
    public MarketOrder {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be positive");
    }
}
