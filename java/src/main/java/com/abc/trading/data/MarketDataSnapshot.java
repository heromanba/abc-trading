package com.abc.trading.data;

/** Immutable quote/trade/reference snapshot used by trigger evaluation. */
public record MarketDataSnapshot(
        String symbol,
        long tsInit,
        double bid,
        double ask,
        double last,
        double mark,
        double index,
        long sequence) {
    public MarketDataSnapshot {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        validatePositive(bid, "bid");
        validatePositive(ask, "ask");
        validatePositive(last, "last");
        validatePositive(mark, "mark");
        validatePositive(index, "index");
        if (bid > ask) throw new IllegalArgumentException("bid must not exceed ask");
    }

    public static MarketDataSnapshot fromBar(Bar bar) {
        return new MarketDataSnapshot(bar.symbol(), bar.tsInit(), bar.close(), bar.close(),
                bar.close(), bar.close(), bar.close(), bar.sequence());
    }

    private static void validatePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}