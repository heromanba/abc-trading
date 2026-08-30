package com.abc.trading.adapters.binance;

import java.math.BigDecimal;

/** Validates core order values against Binance exchangeInfo filters. */
public final class BinanceOrderValidator {
    private BinanceOrderValidator() { }

    public static void validate(BinanceInstrumentMetadata metadata, BigDecimal quantity, BigDecimal price) {
        if (metadata == null) throw new IllegalArgumentException("instrument metadata is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (quantity.compareTo(metadata.minQuantity()) < 0) {
            throw new IllegalArgumentException("quantity is below Binance minQty for " + metadata.symbol());
        }
        if (quantity.stripTrailingZeros().scale() > metadata.sizePrecision()) {
            throw new IllegalArgumentException("quantity exceeds Binance size precision for " + metadata.symbol());
        }
        if (quantity.remainder(metadata.quantityStepSize()).signum() != 0) {
            throw new IllegalArgumentException("quantity does not match Binance stepSize for " + metadata.symbol());
        }
        if (price != null) {
            if (price.signum() <= 0) throw new IllegalArgumentException("price must be positive");
            if (price.remainder(metadata.priceTickSize()).signum() != 0) {
                throw new IllegalArgumentException("price does not match Binance tickSize for " + metadata.symbol());
            }
        }
    }
}
