package com.abc.trading.data;

/** Formula used to calculate initial and maintenance margin. */
public enum MarginModelType {
    NOTIONAL_RATE,
    STANDARD_NOTIONAL_RATE,
    INVERSE_NOTIONAL_RATE,
    FIXED_PER_UNIT
}
