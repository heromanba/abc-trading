package com.abc.trading.data;

/** Market-data FX conversion update used by account valuation. */
public record FxRateUpdate(
        String fromCurrency,
        String toCurrency,
        double rate,
        long tsInit,
        long sequence) {
    public FxRateUpdate {
        if (fromCurrency == null || fromCurrency.isBlank()) throw new IllegalArgumentException("fromCurrency is required");
        if (toCurrency == null || toCurrency.isBlank()) throw new IllegalArgumentException("toCurrency is required");
        if (fromCurrency.equals(toCurrency)) throw new IllegalArgumentException("currencies must differ");
        if (!Double.isFinite(rate) || rate <= 0.0) throw new IllegalArgumentException("rate must be positive");
    }
}
