package com.abc.trading.data;

/** Instrument metadata required by account settlement and margin calculation. */
public record InstrumentSpec(
        String symbol,
        String venue,
        TickScheme tickScheme,
        String baseCurrency,
        String quoteCurrency,
        double marginInitialRate,
        double marginMaintenanceRate) {
    public InstrumentSpec {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (tickScheme == null) throw new IllegalArgumentException("tickScheme is required");
        if (baseCurrency == null || baseCurrency.isBlank()) throw new IllegalArgumentException("baseCurrency is required");
        if (quoteCurrency == null || quoteCurrency.isBlank()) throw new IllegalArgumentException("quoteCurrency is required");
        if (!Double.isFinite(marginInitialRate) || marginInitialRate < 0.0) {
            throw new IllegalArgumentException("marginInitialRate must be finite and non-negative");
        }
        if (!Double.isFinite(marginMaintenanceRate) || marginMaintenanceRate < 0.0) {
            throw new IllegalArgumentException("marginMaintenanceRate must be finite and non-negative");
        }
    }

    public static InstrumentSpec defaults(String symbol, String venue, TickScheme tickScheme) {
        return new InstrumentSpec(symbol, venue, tickScheme, symbol, "USD", 1.0, 0.5);
    }
}
