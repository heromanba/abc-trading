package com.abc.trading.execution;

public record Commission(double amount, String currency) {
    public Commission {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("commission amount must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    public static Commission zero(String currency) {
        return new Commission(0.0, currency);
    }
}
