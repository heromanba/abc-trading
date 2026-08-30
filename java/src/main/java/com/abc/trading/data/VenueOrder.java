package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;

/** One individual venue order in an MBO/L3 book. */
public record VenueOrder(
        String orderId,
        SignalDirection side,
        double price,
        Quantity quantity,
        long sequence) {
    public VenueOrder(String orderId, SignalDirection side, double price, int quantity, long sequence) {
        this(orderId, side, price, Quantity.fromInt(quantity), sequence);
    }

    public VenueOrder {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be positive");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
    }
}