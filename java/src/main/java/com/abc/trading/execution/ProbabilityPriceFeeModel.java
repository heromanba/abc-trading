package com.abc.trading.execution;

public record ProbabilityPriceFeeModel(double makerRate, double takerRate, String currency) implements FeeModel {
    public ProbabilityPriceFeeModel {
        if (makerRate < 0.0 || takerRate < 0.0 || !Double.isFinite(makerRate) || !Double.isFinite(takerRate)) {
            throw new IllegalArgumentException("fee rates must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    @Override
    public Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        if (fillPrice < 0.0 || fillPrice > 1.0) throw new IllegalArgumentException("probability price must be in [0, 1]");
        double rate = liquiditySide == LiquiditySide.MAKER ? makerRate : takerRate;
        double amount = Math.round(fillQuantity * rate * fillPrice * (1.0 - fillPrice) * 100000.0) / 100000.0;
        return new Commission(amount, currency);
    }
}
