package com.abc.trading.portfolio;

/** Published when all positions in a liquidation session have been closed. */
public record LiquidationCompleted(AccountState state) {
    public LiquidationCompleted {
        if (state == null) throw new IllegalArgumentException("state is required");
    }
}
