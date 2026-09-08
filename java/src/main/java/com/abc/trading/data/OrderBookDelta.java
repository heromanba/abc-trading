package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;

/** Immutable L2 order-book mutation, analogous to Nautilus OrderBookDelta. */
public record OrderBookDelta(
        String symbol,
        long tsInit,
        SignalDirection side,
        BookAction action,
        Price price,
        Quantity quantity,
        long sequence) {
    public OrderBookDelta(String symbol, long tsInit, SignalDirection side, BookAction action,
            double price, int quantity, long sequence) {
        this(symbol, tsInit, side, action, Price.fromDouble(price), Quantity.fromInt(quantity), sequence);
    }

    public OrderBookDelta(String symbol, long tsInit, SignalDirection side, BookAction action,
            Price price, int quantity, long sequence) {
        this(symbol, tsInit, side, action, price, Quantity.fromInt(quantity), sequence);
    }

    public OrderBookDelta {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (price == null) throw new IllegalArgumentException("price is required");
        if (action != BookAction.CLEAR && (quantity == null || quantity.isZero())) {
            throw new IllegalArgumentException("quantity must be positive unless clearing");
        }
    }
}