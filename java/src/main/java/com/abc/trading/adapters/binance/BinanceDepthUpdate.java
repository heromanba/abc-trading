package com.abc.trading.adapters.binance;

import java.util.List;

/** Lossless Binance USD-M depthUpdate mapped from Nautilus BinanceOrderBookDelta. */
public record BinanceDepthUpdate(
        String symbol,
        long eventTimeMs,
        long transactionTimeMs,
        long firstUpdateId,
        long lastUpdateId,
        long previousUpdateId,
        List<BinancePriceLevel> bids,
        List<BinancePriceLevel> asks) {
    public BinanceDepthUpdate {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (bids == null || asks == null) throw new IllegalArgumentException("book sides are required");
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
