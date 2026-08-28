package com.abc.trading.adapters.binance;

import java.math.BigDecimal;

public record BinancePriceLevel(BigDecimal price, BigDecimal quantity) {
    public BinancePriceLevel {
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("price must be positive");
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("quantity must be non-negative");
    }
}
