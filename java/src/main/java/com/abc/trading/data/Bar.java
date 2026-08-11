package com.abc.trading.data;

/** Immutable primitive-backed market bar exposed to the Python facade. */
public final class Bar {
    private final String symbol;
    private final long tsInit;
    private final double close;
    private final long sequence;

    public Bar(String symbol, long tsInit, double close, long sequence) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (!Double.isFinite(close)) {
            throw new IllegalArgumentException("close must be finite");
        }
        this.symbol = symbol;
        this.tsInit = tsInit;
        this.close = close;
        this.sequence = sequence;
    }

    public String symbol() {
        return symbol;
    }

    public long tsInit() {
        return tsInit;
    }

    public double close() {
        return close;
    }

    public long sequence() {
        return sequence;
    }
}
