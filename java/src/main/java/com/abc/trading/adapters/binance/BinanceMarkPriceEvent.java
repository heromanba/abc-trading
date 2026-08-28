package com.abc.trading.adapters.binance;

import java.math.BigDecimal;

/** Binance mark-price update used by Nautilus for futures valuation and triggers. */
public record BinanceMarkPriceEvent(
        String symbol,
        long eventTimeMs,
        BigDecimal markPrice,
        BigDecimal indexPrice,
        long nextFundingTimeMs) {
    public BinanceMarkPriceEvent {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (markPrice == null || markPrice.signum() <= 0) throw new IllegalArgumentException("markPrice must be positive");
        if (indexPrice == null || indexPrice.signum() <= 0) throw new IllegalArgumentException("indexPrice must be positive");
    }
}
