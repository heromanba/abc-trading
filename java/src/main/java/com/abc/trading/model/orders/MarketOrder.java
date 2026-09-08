package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

public record MarketOrder(
        String clientOrderId,
        String strategyId,
        String symbol,
        SignalDirection side,
        Quantity quantity,
        Price price,
        long timestampNs
) implements Order {
    public MarketOrder {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (price == null) throw new IllegalArgumentException("price is required");
    }

    public MarketOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            int quantity, double price, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, Quantity.fromInt(quantity), Price.fromDouble(price), timestampNs);
    }

    public MarketOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            Quantity quantity, double price, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, quantity, Price.fromDouble(price), timestampNs);
    }
}
