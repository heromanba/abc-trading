package com.abc.trading.data;

import java.math.BigDecimal;

/** Instrument metadata required by account settlement and margin calculation. */
public record InstrumentSpec(
        String symbol,
        String venue,
        TickScheme tickScheme,
        String baseCurrency,
        String quoteCurrency,
        double marginInitialRate,
        double marginMaintenanceRate,
        MarginModelType marginModelType,
        double initialMarginPerUnit,
        double maintenanceMarginPerUnit,
        int sizePrecision,
        BigDecimal sizeIncrement) {
    public InstrumentSpec(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate) {
        this(symbol, venue, tickScheme, baseCurrency, quoteCurrency, marginInitialRate,
                marginMaintenanceRate, MarginModelType.NOTIONAL_RATE, 0.0, 0.0);
    }

    public InstrumentSpec(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit) {
        this(symbol, venue, tickScheme, baseCurrency, quoteCurrency, marginInitialRate,
                marginMaintenanceRate, marginModelType, initialMarginPerUnit, maintenanceMarginPerUnit,
                0, BigDecimal.ONE);
    }

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
        if (marginModelType == null) throw new IllegalArgumentException("marginModelType is required");
        if (!Double.isFinite(initialMarginPerUnit) || initialMarginPerUnit < 0.0) {
            throw new IllegalArgumentException("initialMarginPerUnit must be finite and non-negative");
        }
        if (!Double.isFinite(maintenanceMarginPerUnit) || maintenanceMarginPerUnit < 0.0) {
            throw new IllegalArgumentException("maintenanceMarginPerUnit must be finite and non-negative");
        }
        if (sizePrecision < 0 || sizePrecision > 18) {
            throw new IllegalArgumentException("sizePrecision must be in 0..18");
        }
        if (sizeIncrement == null || sizeIncrement.signum() <= 0) {
            throw new IllegalArgumentException("sizeIncrement must be positive");
        }
        if (sizeIncrement.stripTrailingZeros().scale() > sizePrecision) {
            throw new IllegalArgumentException("sizeIncrement exceeds sizePrecision");
        }
    }

    public static InstrumentSpec defaults(String symbol, String venue, TickScheme tickScheme) {
        return new InstrumentSpec(symbol, venue, tickScheme, symbol, "USD", 1.0, 0.5,
                MarginModelType.NOTIONAL_RATE, 0.0, 0.0, 0, BigDecimal.ONE);
    }

    public void validateQuantity(Quantity quantity) {
        if (quantity == null || quantity.isZero()) {
            throw new IllegalArgumentException("quantity must be positive for " + symbol);
        }
        BigDecimal value = quantity.asDecimal();
        if (value.stripTrailingZeros().scale() > sizePrecision) {
            throw new IllegalArgumentException("quantity exceeds sizePrecision for " + symbol);
        }
        if (value.remainder(sizeIncrement).signum() != 0) {
            throw new IllegalArgumentException("quantity does not match sizeIncrement for " + symbol);
        }
    }
}
