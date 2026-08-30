package com.abc.trading.portfolio;

import com.abc.trading.data.Quantity;

/** Published when a breached account begins forced position liquidation. */
public record LiquidationStarted(
        AccountState state,
        String symbol,
        String liquidationOrderId,
        Quantity quantity) {
    public LiquidationStarted {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (liquidationOrderId == null || liquidationOrderId.isBlank()) throw new IllegalArgumentException("liquidationOrderId is required");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
    }

    public LiquidationStarted(AccountState state, String symbol, String liquidationOrderId, int quantity) {
        this(state, symbol, liquidationOrderId, Quantity.fromInt(quantity));
    }
}
