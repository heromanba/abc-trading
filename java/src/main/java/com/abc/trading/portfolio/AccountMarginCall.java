package com.abc.trading.portfolio;

/** Published when account equity falls below maintenance margin. */
public record AccountMarginCall(AccountState state) {
    public AccountMarginCall {
        if (state == null || !state.marginCall()) throw new IllegalArgumentException("margin-call state is required");
    }
}
