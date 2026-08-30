package com.abc.trading.portfolio;

import java.math.BigDecimal;
import java.util.Objects;

/** Balance view for one account currency with exact decimal accessors. */
public final class AccountBalance {
    private final String currency;
    private final BigDecimal totalDecimal;
    private final BigDecimal lockedDecimal;
    private final BigDecimal freeDecimal;

    public AccountBalance(String currency, double total, double locked, double free) {
        this(currency, BigDecimal.valueOf(total), BigDecimal.valueOf(locked), BigDecimal.valueOf(free));
    }

    public AccountBalance(String currency, BigDecimal total, BigDecimal locked, BigDecimal free) {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        totalDecimal = Objects.requireNonNull(total, "total");
        lockedDecimal = Objects.requireNonNull(locked, "locked");
        freeDecimal = Objects.requireNonNull(free, "free");
        if (locked.signum() < 0) throw new IllegalArgumentException("locked must be non-negative");
        if (total.compareTo(locked.add(free)) != 0) throw new IllegalArgumentException("total must equal locked + free");
        this.currency = currency;
    }

    public String currency() { return currency; }
    public double total() { return totalDecimal.doubleValue(); }
    public double locked() { return lockedDecimal.doubleValue(); }
    public double free() { return freeDecimal.doubleValue(); }
    public BigDecimal totalDecimal() { return totalDecimal; }
    public BigDecimal lockedDecimal() { return lockedDecimal; }
    public BigDecimal freeDecimal() { return freeDecimal; }

    @Override
    public boolean equals(Object other) {
        return other instanceof AccountBalance balance
                && currency.equals(balance.currency)
                && totalDecimal.compareTo(balance.totalDecimal) == 0
                && lockedDecimal.compareTo(balance.lockedDecimal) == 0
                && freeDecimal.compareTo(balance.freeDecimal) == 0;
    }

    @Override
    public int hashCode() {
        int result = currency.hashCode();
        result = 31 * result + totalDecimal.stripTrailingZeros().hashCode();
        result = 31 * result + lockedDecimal.stripTrailingZeros().hashCode();
        return 31 * result + freeDecimal.stripTrailingZeros().hashCode();
    }
}
