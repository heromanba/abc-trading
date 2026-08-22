package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;

/** Immutable L2 order-book mutation, analogous to Nautilus OrderBookDelta. */
public record OrderBookDelta(
        String symbol,
        long tsInit,
        SignalDirection side,
        BookAction action,
        double price,
        int quantity,
        long sequence) {
    public OrderBookDelta {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be positive");
        if (action != BookAction.CLEAR && quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive unless clearing");
        }
    }
}