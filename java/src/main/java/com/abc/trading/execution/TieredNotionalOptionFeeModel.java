package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public record TieredNotionalOptionFeeModel(double makerRate, double takerRate, String currency) implements FeeModel {
    public TieredNotionalOptionFeeModel {
        if (makerRate < 0.0 || takerRate < 0.0 || !Double.isFinite(makerRate) || !Double.isFinite(takerRate)) {
            throw new IllegalArgumentException("fee rates must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    @Override
    public Commission calculate(Quantity fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        double rate = liquiditySide == LiquiditySide.MAKER ? makerRate : takerRate;
        return new Commission(fillQuantity.asDecimal()
            .multiply(java.math.BigDecimal.valueOf(fillPrice), java.math.MathContext.DECIMAL128)
            .multiply(java.math.BigDecimal.valueOf(rate), java.math.MathContext.DECIMAL128), currency);
    }
}
