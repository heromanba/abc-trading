package com.abc.trading.execution;

public interface FeeModel {
    Commission calculate(int fillQuantity, double fillPrice, LiquiditySide liquiditySide);
}
