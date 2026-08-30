package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public record PerContractFeeModel(double commissionPerContract, String currency) implements FeeModel {
    public PerContractFeeModel {
        if (!Double.isFinite(commissionPerContract) || commissionPerContract < 0.0) {
            throw new IllegalArgumentException("commissionPerContract must be non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    @Override
    public Commission calculate(Quantity fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        if (fillQuantity == null || fillQuantity.isZero()) throw new IllegalArgumentException("fillQuantity must be positive");
        return new Commission(commissionPerContract * fillQuantity.asDouble(), currency);
    }
}
