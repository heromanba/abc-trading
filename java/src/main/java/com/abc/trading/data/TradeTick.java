package com.abc.trading.data;

/** Immutable market trade used to advance L3 queue positions. */
public record TradeTick(
        String symbol,
        long tsInit,
        Price price,
        Quantity quantity,
        AggressorSide aggressorSide,
        long sequence) {
    public TradeTick(String symbol, long tsInit, double price, int quantity,
            AggressorSide aggressorSide, long sequence) {
        this(symbol, tsInit, Price.fromDouble(price), Quantity.fromInt(quantity), aggressorSide, sequence);
    }

    public TradeTick {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (price == null) throw new IllegalArgumentException("price is required");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (aggressorSide == null) throw new IllegalArgumentException("aggressorSide is required");
    }

    public TradeTick(String symbol, long tsInit, Price price, int quantity,
            AggressorSide aggressorSide, long sequence) {
        this(symbol, tsInit, price, Quantity.fromInt(quantity), aggressorSide, sequence);
    }

    public double priceDouble() { return price.asDouble(); }
}
