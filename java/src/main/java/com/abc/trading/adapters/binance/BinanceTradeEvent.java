package com.abc.trading.adapters.binance;

import java.math.BigDecimal;

/** Lossless Binance aggTrade mapped to a Rust TradeTick aggressor side. */
public record BinanceTradeEvent(
        String symbol,
        long eventTimeMs,
        long tradeTimeMs,
        long tradeId,
        BigDecimal price,
        BigDecimal quantity,
        boolean buyerIsMaker) {
    public String aggressorSide() { return buyerIsMaker ? "SELLER" : "BUYER"; }
}
