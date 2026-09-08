package com.abc.trading.data;

/** Immutable aggregate fixed-point quantity at one price level. */
public record BookLevel(Price price, Quantity quantity) {
    public BookLevel(double price, int quantity) {
        this(Price.fromDouble(price), Quantity.fromInt(quantity));
    }

    public BookLevel(double price, Quantity quantity) { this(Price.fromDouble(price), quantity); }
    public BookLevel(Price price, int quantity) { this(price, Quantity.fromInt(quantity)); }

    public BookLevel {
        if (price == null) throw new IllegalArgumentException("price is required");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
    }
}