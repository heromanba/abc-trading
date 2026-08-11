package com.abc.trading.model;

/** Immutable numeric value used at the Java/Python boundary. */
public record Money(double amount, String currency) {
    public Money {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount must be finite");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }
}
