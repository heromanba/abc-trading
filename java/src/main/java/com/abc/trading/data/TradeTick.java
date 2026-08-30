package com.abc.trading.data;

/** Immutable market trade used to advance L3 queue positions. */
public record TradeTick(
        String symbol,
        long tsInit,
        double price,
        Quantity quantity,
        AggressorSide aggressorSide,
        long sequence) {
    public TradeTick(String symbol, long tsInit, double price, int quantity,
            AggressorSide aggressorSide, long sequence) {
        this(symbol, tsInit, price, Quantity.fromInt(quantity), aggressorSide, sequence);
    }

    public TradeTick {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be positive");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (aggressorSide == null) throw new IllegalArgumentException("aggressorSide is required");
    }
}
