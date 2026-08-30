package com.abc.trading.data;

/** Immutable aggregate fixed-point quantity at one price level. */
public record BookLevel(double price, Quantity quantity) {
    public BookLevel(double price, int quantity) {
        this(price, Quantity.fromInt(quantity));
    }

    public BookLevel {
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("price must be finite and positive");
        }
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
    }
}