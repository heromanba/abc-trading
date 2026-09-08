package com.abc.trading.data;

import java.util.Objects;

/** Immutable quote/trade/reference snapshot used by trigger evaluation. */
public record MarketDataSnapshot(
        String symbol,
        long tsInit,
        Price bid,
        Price ask,
        Price last,
        Price mark,
        Price index,
        long sequence) {
    public MarketDataSnapshot {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        Objects.requireNonNull(bid, "bid");
        Objects.requireNonNull(ask, "ask");
        Objects.requireNonNull(last, "last");
        Objects.requireNonNull(mark, "mark");
        Objects.requireNonNull(index, "index");
        if (bid.compareTo(ask) > 0) throw new IllegalArgumentException("bid must not exceed ask");
    }

    public MarketDataSnapshot(String symbol, long tsInit, double bid, double ask, double last,
            double mark, double index, long sequence) {
        this(symbol, tsInit, Price.fromDouble(bid), Price.fromDouble(ask), Price.fromDouble(last),
                Price.fromDouble(mark), Price.fromDouble(index), sequence);
    }

    public double bidDouble() { return bid.asDouble(); }
    public double askDouble() { return ask.asDouble(); }
    public double lastDouble() { return last.asDouble(); }
    public double markDouble() { return mark.asDouble(); }
    public double indexDouble() { return index.asDouble(); }

    public static MarketDataSnapshot fromBar(Bar bar) {
        return new MarketDataSnapshot(bar.symbol(), bar.tsInit(), bar.close(), bar.close(),
                bar.close(), bar.close(), bar.close(), bar.sequence());
    }

}