package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;

/** One individual venue order in an MBO/L3 book. */
public record VenueOrder(
        String orderId,
        SignalDirection side,
        Price price,
        Quantity quantity,
        long sequence) {
    public VenueOrder(String orderId, SignalDirection side, double price, int quantity, long sequence) {
        this(orderId, side, Price.fromDouble(price), Quantity.fromInt(quantity), sequence);
    }

    public VenueOrder {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (price == null) throw new IllegalArgumentException("price is required");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
    }

    public VenueOrder(String orderId, SignalDirection side, Price price, int quantity, long sequence) {
        this(orderId, side, price, Quantity.fromInt(quantity), sequence);
    }
    public VenueOrder(String orderId, SignalDirection side, double price, Quantity quantity, long sequence) {
        this(orderId, side, Price.fromDouble(price), quantity, sequence);
    }

    public double priceDouble() { return price.asDouble(); }
}