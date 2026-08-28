package com.abc.trading.adapters.binance;

import java.math.BigDecimal;
import java.util.Map;

/** Binance ACCOUNT_UPDATE balance and position payload projection. */
public record BinanceAccountUpdate(
        long eventTimeMs,
        Map<String, BigDecimal> walletBalances,
        Map<String, BigDecimal> marginBalances,
        Map<String, BigDecimal> unrealizedPnl) {
    public BinanceAccountUpdate {
        if (walletBalances == null || marginBalances == null || unrealizedPnl == null) {
            throw new IllegalArgumentException("account maps are required");
        }
        walletBalances = Map.copyOf(walletBalances);
        marginBalances = Map.copyOf(marginBalances);
        unrealizedPnl = Map.copyOf(unrealizedPnl);
    }
}
