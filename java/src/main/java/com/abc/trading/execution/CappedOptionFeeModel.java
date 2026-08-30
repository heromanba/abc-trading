package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public record CappedOptionFeeModel(
        double makerRate,
        double takerRate,
        double capPerContract,
        double multiplier,
        String currency) implements FeeModel {
    public CappedOptionFeeModel {
        if (makerRate < 0.0 || takerRate < 0.0 || capPerContract < 0.0 || multiplier <= 0.0) {
            throw new IllegalArgumentException("option fee parameters are invalid");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    @Override
    public Commission calculate(Quantity fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        double rate = liquiditySide == LiquiditySide.MAKER ? makerRate : takerRate;
        double feePerContract = Math.min(rate, capPerContract * fillPrice) * multiplier;
        return new Commission(feePerContract * fillQuantity.asDouble(), currency);
    }
}
