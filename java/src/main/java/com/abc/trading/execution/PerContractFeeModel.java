package com.abc.trading.execution;

public record PerContractFeeModel(double commissionPerContract, String currency) implements FeeModel {
    public PerContractFeeModel {
        if (!Double.isFinite(commissionPerContract) || commissionPerContract < 0.0) {
            throw new IllegalArgumentException("commissionPerContract must be non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    @Override
    public Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        if (fillQuantity <= 0) throw new IllegalArgumentException("fillQuantity must be positive");
        return new Commission(commissionPerContract * fillQuantity, currency);
    }
}
