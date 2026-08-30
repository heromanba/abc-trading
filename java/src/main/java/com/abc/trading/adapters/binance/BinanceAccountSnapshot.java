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
        BigDecimal locked = walletBalance.subtract(availableBalance);
        BigDecimal equity = walletBalance.add(totalUnrealizedProfit);
        return new AccountState(venue, currency, walletBalance, locked, availableBalance,
            totalInitialMargin, totalMaintenanceMargin,
                updateTimeMs * 1_000_000L,
            Map.of(currency, new AccountBalance(currency, walletBalance, locked, availableBalance)),
            totalUnrealizedProfit, equity,
            equity.compareTo(totalMaintenanceMargin) < 0,
            equity.compareTo(totalMaintenanceMargin) < 0);
    }
}
