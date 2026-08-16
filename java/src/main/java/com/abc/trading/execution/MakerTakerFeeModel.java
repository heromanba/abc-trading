package com.abc.trading.execution;

public record MakerTakerFeeModel(
        double makerRate,
        double takerRate,
        String currency) implements FeeModel {
    public MakerTakerFeeModel {
        if (!Double.isFinite(makerRate) || makerRate < 0.0 || !Double.isFinite(takerRate) || takerRate < 0.0) {
            throw new IllegalArgumentException("fee rates must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    public static MakerTakerFeeModel zero() {
        return new MakerTakerFeeModel(0.0, 0.0, "USD");
    }

    @Override
    public Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        if (fillQuantity <= 0) throw new IllegalArgumentException("fillQuantity must be positive");
        if (!Double.isFinite(fillPrice) || fillPrice <= 0.0) throw new IllegalArgumentException("fillPrice must be positive");
        if (liquiditySide == null) throw new IllegalArgumentException("liquiditySide is required");
        double rate = liquiditySide == LiquiditySide.MAKER ? makerRate : takerRate;
        return new Commission(fillQuantity * fillPrice * rate, currency);
    }
}
