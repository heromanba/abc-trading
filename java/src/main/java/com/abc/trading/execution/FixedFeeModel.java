package com.abc.trading.execution;

public final class FixedFeeModel implements FeeModel {
    private final Commission commission;
    private final boolean chargeOnce;
    private boolean charged;

    public FixedFeeModel(Commission commission, boolean chargeOnce) {
        this.commission = commission;
        this.chargeOnce = chargeOnce;
    }

    @Override
    public Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        if (chargeOnce && charged) return Commission.zero(commission.currency());
        charged = true;
        return commission;
    }
}
