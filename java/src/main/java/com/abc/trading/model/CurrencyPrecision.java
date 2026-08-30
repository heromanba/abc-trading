package com.abc.trading.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/** Currency scale policy for explicit monetary rounding at external boundaries. */
public final class CurrencyPrecision {
    private static final Map<String, Integer> DEFAULT_SCALES = Map.of(
            "USD", 2,
            "EUR", 2,
            "GBP", 2,
            "USDT", 8,
            "BTC", 8,
            "ETH", 8);

    private CurrencyPrecision() { }

    public static int scale(String currency) {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        return DEFAULT_SCALES.getOrDefault(currency.toUpperCase(Locale.ROOT), 8);
    }

    public static BigDecimal round(BigDecimal amount, String currency, RoundingMode roundingMode) {
        if (amount == null) throw new IllegalArgumentException("amount is required");
        if (roundingMode == null) throw new IllegalArgumentException("roundingMode is required");
        return amount.setScale(scale(currency), roundingMode);
    }
}