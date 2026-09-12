package com.abc.trading.portfolio;

import com.abc.trading.data.OptionSpec;
import com.abc.trading.data.Price;
import com.abc.trading.data.Quantity;

import java.math.BigDecimal;

/** Cross/isolated option margin helper using exact option contract metadata. */
public final class OptionMarginModel {
    private OptionMarginModel() { }

    public static BigDecimal initial(OptionSpec option, Price underlyingPrice, BigDecimal signedQuantity,
            MarginMode mode) {
        return option.marginRequirement(underlyingPrice, Quantity.fromDecimal(signedQuantity.abs(), signedQuantity.scale()), signedQuantity.signum() < 0);
    }

    public static BigDecimal maintenance(OptionSpec option, Price underlyingPrice, BigDecimal signedQuantity,
            MarginMode mode) {
        return initial(option, underlyingPrice, signedQuantity, mode)
                .multiply(new BigDecimal("0.5"));
    }
}
