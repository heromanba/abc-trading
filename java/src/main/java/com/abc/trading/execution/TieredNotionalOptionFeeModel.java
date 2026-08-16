package com.abc.trading.execution;

public record TieredNotionalOptionFeeModel(double makerRate, double takerRate, String currency) implements FeeModel {
    public TieredNotionalOptionFeeModel {
        if (makerRate < 0.0 || takerRate < 0.0 || !Double.isFinite(makerRate) || !Double.isFinite(takerRate)) {
            throw new IllegalArgumentException("fee rates must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    @Override
    public Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        double rate = liquiditySide == LiquiditySide.MAKER ? makerRate : takerRate;
        return new Commission(fillQuantity * fillPrice * rate, currency);
    }
}
