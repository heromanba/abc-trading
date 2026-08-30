package com.abc.trading.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable monetary value with an exact decimal representation. */
public final class Money {
    private final BigDecimal amountDecimal;
    private final String currency;

    public Money(double amount, String currency) {
        this(BigDecimal.valueOf(amount), currency);
    }

    public Money(BigDecimal amount, String currency) {
        amountDecimal = Objects.requireNonNull(amount, "amount");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        this.currency = currency;
    }

    public double amount() { return amountDecimal.doubleValue(); }
    public BigDecimal amountDecimal() { return amountDecimal; }
    public String currency() { return currency; }

    public Money rounded(RoundingMode roundingMode) {
        return new Money(CurrencyPrecision.round(amountDecimal, currency, roundingMode), currency);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money
                && currency.equals(money.currency)
                && amountDecimal.compareTo(money.amountDecimal) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * amountDecimal.stripTrailingZeros().hashCode() + currency.hashCode();
    }

    @Override
    public String toString() { return amountDecimal.toPlainString() + " " + currency; }
}
