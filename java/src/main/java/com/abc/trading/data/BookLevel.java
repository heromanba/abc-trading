package com.abc.trading.data;

/** Immutable aggregate quantity at one price level. */
public record BookLevel(double price, int quantity) {
    public BookLevel {
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("price must be finite and positive");
        }
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}