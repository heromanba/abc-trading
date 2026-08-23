package com.abc.trading.portfolio;

/** Account-state publication emitted after a portfolio/account mutation. */
public record AccountStateEvent(AccountState state) {
    public AccountStateEvent {
        if (state == null) throw new IllegalArgumentException("state is required");
    }
}
