package com.abc.trading.data;

/** Instrument-owned fixed tick increment used by TICKS trailing offsets. */
public final class TickScheme {
    private final double tickSize;

    private TickScheme(double tickSize) {
        this.tickSize = tickSize;
    }

    public static TickScheme fixed(double tickSize) {
        if (!Double.isFinite(tickSize) || tickSize <= 0.0) {
            throw new IllegalArgumentException("tickSize must be finite and positive");
        }
        return new TickScheme(tickSize);
    }

    public double tickSize(double price) {
        if (!Double.isFinite(price) || price < 0.0) {
            throw new IllegalArgumentException("price must be finite and non-negative");
        }
        return tickSize;
    }
}
