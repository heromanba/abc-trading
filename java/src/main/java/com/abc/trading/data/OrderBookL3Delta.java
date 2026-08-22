package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;

/** One individual venue-order mutation for an MBO/L3 book. */
public record OrderBookL3Delta(
        String symbol,
        long tsInit,
        SignalDirection side,
        BookAction action,
        String orderId,
        double price,
        int quantity,
        long sequence) {
    public OrderBookL3Delta {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (action != BookAction.CLEAR && (orderId == null || orderId.isBlank())) throw new IllegalArgumentException("orderId is required");
        if (action != BookAction.CLEAR && (!Double.isFinite(price) || price <= 0.0)) throw new IllegalArgumentException("price must be positive");
        if (action == BookAction.ADD && quantity <= 0) throw new IllegalArgumentException("ADD quantity must be positive");
        if (action == BookAction.UPDATE && quantity < 0) throw new IllegalArgumentException("UPDATE quantity must be non-negative");
    }
}