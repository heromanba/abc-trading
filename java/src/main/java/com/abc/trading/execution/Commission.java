package com.abc.trading.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Commission {
    private final BigDecimal amountDecimal;
    private final String currency;

    public Commission(double amount, String currency) {
        this(BigDecimal.valueOf(amount), currency);
    }

    public Commission(BigDecimal amount, String currency) {
        amountDecimal = Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("commission amount must be non-negative");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        this.currency = currency;
    }

    public double amount() { return amountDecimal.doubleValue(); }
    public BigDecimal amountDecimal() { return amountDecimal; }
    public String currency() { return currency; }

    public Commission rounded(RoundingMode roundingMode) {
        return new Commission(com.abc.trading.model.CurrencyPrecision.round(amountDecimal, currency, roundingMode), currency);
    }

    public static Commission zero(String currency) { return new Commission(BigDecimal.ZERO, currency); }

    @Override
    public boolean equals(Object other) {
        return other instanceof Commission commission
                && currency.equals(commission.currency)
                && amountDecimal.compareTo(commission.amountDecimal) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * amountDecimal.stripTrailingZeros().hashCode() + currency.hashCode();
    }

    @Override
    public String toString() { return amountDecimal.toPlainString() + " " + currency; }
}
