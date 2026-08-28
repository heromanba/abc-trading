package com.abc.trading.adapters.binance;

import com.abc.trading.portfolio.AccountBalance;
import com.abc.trading.portfolio.AccountState;

import java.math.BigDecimal;
import java.util.Map;

/** Binance USD-M account snapshot mapped from the signed account endpoint. */
public record BinanceAccountSnapshot(
        long updateTimeMs,
        String currency,
        BigDecimal walletBalance,
        BigDecimal availableBalance,
        BigDecimal totalInitialMargin,
        BigDecimal totalMaintenanceMargin,
        BigDecimal totalUnrealizedProfit) {
    public BinanceAccountSnapshot {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (walletBalance == null || availableBalance == null || totalInitialMargin == null
                || totalMaintenanceMargin == null || totalUnrealizedProfit == null) {
            throw new IllegalArgumentException("account values are required");
        }
    }

    public AccountState toAccountState(String venue) {
        double total = walletBalance.doubleValue();
        double free = availableBalance.doubleValue();
        double locked = total - free;
        double unrealized = totalUnrealizedProfit.doubleValue();
        return new AccountState(venue, currency, total, locked, free,
                totalInitialMargin.doubleValue(), totalMaintenanceMargin.doubleValue(),
                updateTimeMs * 1_000_000L,
                Map.of(currency, new AccountBalance(currency, total, locked, free)),
                unrealized, total + unrealized,
                total + unrealized < totalMaintenanceMargin.doubleValue(),
                total + unrealized < totalMaintenanceMargin.doubleValue());
    }
}
