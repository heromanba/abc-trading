package com.abc.trading.data;

/** Contract settlement and PnL convention for an instrument. */
public enum DerivativeType {
    SPOT,
    LINEAR_FUTURE,
    LINEAR_PERPETUAL,
    INVERSE_FUTURE,
    INVERSE_PERPETUAL;

    public boolean isInverse() {
        return this == INVERSE_FUTURE || this == INVERSE_PERPETUAL;
    }

    public boolean isDerivative() {
        return this != SPOT;
    }

    public boolean isPerpetual() {
        return this == LINEAR_PERPETUAL || this == INVERSE_PERPETUAL;
    }
}