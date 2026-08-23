package com.abc.trading.portfolio;

/** Balance view for one account currency. */
public record AccountBalance(
        String currency,
        double total,
        double locked,
        double free) {
    public AccountBalance {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (!Double.isFinite(total) || !Double.isFinite(locked) || !Double.isFinite(free)) {
            throw new IllegalArgumentException("balance values must be finite");
        }
        if (locked < 0.0) throw new IllegalArgumentException("locked must be non-negative");
        if (Math.abs(total - locked - free) > 1e-9) {
            throw new IllegalArgumentException("total must equal locked + free");
        }
    }
}
