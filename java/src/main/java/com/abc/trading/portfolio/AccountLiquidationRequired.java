package com.abc.trading.portfolio;

/** Published when account equity cannot support maintenance margin. */
public record AccountLiquidationRequired(AccountState state) {
    public AccountLiquidationRequired {
        if (state == null || !state.liquidationRequired()) throw new IllegalArgumentException("liquidation state is required");
    }
}
