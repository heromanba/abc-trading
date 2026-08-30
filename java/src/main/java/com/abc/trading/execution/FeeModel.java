package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public interface FeeModel {
    Commission calculate(Quantity fillQuantity, double fillPrice, LiquiditySide liquiditySide);

    default Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide) {
        return calculate(Quantity.fromInt(fillQuantity), fillPrice, liquiditySide);
    }
}
