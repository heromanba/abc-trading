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
        BigDecimal sizeIncrement,
        int pricePrecision,
        BigDecimal priceTickSize,
        DerivativeType derivativeType,
        BigDecimal contractMultiplier,
        String settlementCurrency) {
    private static final java.math.MathContext DECIMAL_CONTEXT = java.math.MathContext.DECIMAL128;
    public InstrumentSpec(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate) {
        this(symbol, venue, tickScheme, baseCurrency, quoteCurrency, marginInitialRate,
            marginMaintenanceRate, MarginModelType.NOTIONAL_RATE, 0.0, 0.0,
            0, BigDecimal.ONE, precisionOf(tickScheme.tickSize(0.0)),
            BigDecimal.valueOf(tickScheme.tickSize(0.0)), DerivativeType.SPOT,
            BigDecimal.ONE, quoteCurrency);
    }

    public InstrumentSpec(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit) {
        this(symbol, venue, tickScheme, baseCurrency, quoteCurrency, marginInitialRate,
                marginMaintenanceRate, marginModelType, initialMarginPerUnit, maintenanceMarginPerUnit,
                0, BigDecimal.ONE, precisionOf(tickScheme.tickSize(0.0)), BigDecimal.valueOf(tickScheme.tickSize(0.0)),
                DerivativeType.SPOT, BigDecimal.ONE, quoteCurrency);
    }

    public InstrumentSpec(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit,
            int sizePrecision, BigDecimal sizeIncrement, int pricePrecision,
            BigDecimal priceTickSize) {
        this(symbol, venue, tickScheme, baseCurrency, quoteCurrency, marginInitialRate,
            marginMaintenanceRate, marginModelType, initialMarginPerUnit, maintenanceMarginPerUnit,
            sizePrecision, sizeIncrement, pricePrecision, priceTickSize, DerivativeType.SPOT,
            BigDecimal.ONE, quoteCurrency);
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
        if (pricePrecision < 0 || pricePrecision > 18) {
            throw new IllegalArgumentException("pricePrecision must be in 0..18");
        }
        if (priceTickSize == null || priceTickSize.signum() <= 0) {
            throw new IllegalArgumentException("priceTickSize must be positive");
        }
        if (priceTickSize.stripTrailingZeros().scale() > pricePrecision) {
            throw new IllegalArgumentException("priceTickSize exceeds pricePrecision");
        }
        if (derivativeType == null) throw new IllegalArgumentException("derivativeType is required");
        if (contractMultiplier == null || contractMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("contractMultiplier must be positive");
        }
        if (settlementCurrency == null || settlementCurrency.isBlank()) {
            throw new IllegalArgumentException("settlementCurrency is required");
        }
    }

    public static InstrumentSpec defaults(String symbol, String venue, TickScheme tickScheme) {
        return new InstrumentSpec(symbol, venue, tickScheme, symbol, "USD", 1.0, 0.5,
            MarginModelType.NOTIONAL_RATE, 0.0, 0.0, 0, BigDecimal.ONE,
            precisionOf(tickScheme.tickSize(0.0)), BigDecimal.valueOf(tickScheme.tickSize(0.0)),
            DerivativeType.SPOT, BigDecimal.ONE, "USD");
    }

    private static int precisionOf(double value) {
        return Math.max(0, BigDecimal.valueOf(value).stripTrailingZeros().scale());
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

    public void validatePrice(double price) {
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("price must be finite and positive for " + symbol);
        }
        BigDecimal value = BigDecimal.valueOf(price);
        if (value.stripTrailingZeros().scale() > pricePrecision) {
            throw new IllegalArgumentException("price exceeds pricePrecision for " + symbol);
        }
        if (value.remainder(priceTickSize).signum() != 0) {
            throw new IllegalArgumentException("price does not match priceTickSize for " + symbol);
        }
    }

    /** Returns signed realized or unrealized PnL for a position quantity. */
    public BigDecimal calculatePnl(BigDecimal signedQuantity, BigDecimal openPrice, BigDecimal closePrice) {
        if (signedQuantity == null || openPrice == null || closePrice == null) {
            throw new IllegalArgumentException("PnL inputs are required");
        }
        if (derivativeType.isInverse()) {
            if (openPrice.signum() <= 0 || closePrice.signum() <= 0) {
                throw new IllegalArgumentException("inverse contract prices must be positive");
            }
            BigDecimal points = BigDecimal.ONE.divide(openPrice, DECIMAL_CONTEXT)
                .subtract(BigDecimal.ONE.divide(closePrice, DECIMAL_CONTEXT), DECIMAL_CONTEXT);
            if (signedQuantity.signum() < 0) points = points.negate();
            return signedQuantity.abs().multiply(contractMultiplier, DECIMAL_CONTEXT)
                .multiply(points, DECIMAL_CONTEXT);
        }
        return signedQuantity.multiply(contractMultiplier, DECIMAL_CONTEXT)
            .multiply(closePrice.subtract(openPrice), DECIMAL_CONTEXT);
    }

    /** Returns absolute contract notional in the instrument settlement currency. */
    public BigDecimal notional(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("notional inputs are invalid");
        }
        if (derivativeType.isInverse()) {
            if (price.signum() <= 0) throw new IllegalArgumentException("inverse price must be positive");
            return quantity.multiply(contractMultiplier, DECIMAL_CONTEXT)
                .divide(price, DECIMAL_CONTEXT);
        }
        return quantity.multiply(contractMultiplier, DECIMAL_CONTEXT).multiply(price, DECIMAL_CONTEXT);
    }
}
