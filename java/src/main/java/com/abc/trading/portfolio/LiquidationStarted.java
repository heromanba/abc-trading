package com.abc.trading.portfolio;

/** Published when a breached account begins forced position liquidation. */
public record LiquidationStarted(
        AccountState state,
        String symbol,
        String liquidationOrderId,
        int quantity) {
    public LiquidationStarted {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (liquidationOrderId == null || liquidationOrderId.isBlank()) throw new IllegalArgumentException("liquidationOrderId is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
