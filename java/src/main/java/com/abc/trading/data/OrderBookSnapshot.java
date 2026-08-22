package com.abc.trading.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable L2 order-book snapshot ordered by price priority. */
public record OrderBookSnapshot(
        String symbol,
        long tsInit,
        List<BookLevel> bids,
        List<BookLevel> asks,
        long sequence) {
    public OrderBookSnapshot {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (bids == null || asks == null || (bids.isEmpty() && asks.isEmpty())) {
            throw new IllegalArgumentException("at least one book side is required");
        }
        bids = sortedLevels(bids, Comparator.comparingDouble(BookLevel::price).reversed(), "bids");
        asks = sortedLevels(asks, Comparator.comparingDouble(BookLevel::price), "asks");
        if (!bids.isEmpty() && !asks.isEmpty() && bids.get(0).price() > asks.get(0).price()) {
            throw new IllegalArgumentException("best bid must not exceed best ask");
        }
    }

    public static OrderBookSnapshot fromMarketData(MarketDataSnapshot marketData) {
        return new OrderBookSnapshot(marketData.symbol(), marketData.tsInit(),
                List.of(new BookLevel(marketData.bid(), Integer.MAX_VALUE)),
                List.of(new BookLevel(marketData.ask(), Integer.MAX_VALUE)), marketData.sequence());
    }

    private static List<BookLevel> sortedLevels(List<BookLevel> levels, Comparator<BookLevel> comparator,
            String name) {
        if (levels == null) throw new IllegalArgumentException(name + " are required");
        List<BookLevel> copy = new ArrayList<>(levels);
        copy.sort(comparator);
        return List.copyOf(copy);
    }
}