package com.abc.trading.events;

/** Reconstructed account snapshot derived from canonical persisted events. */
public record ReplayAccountState(
        String currency,
        double total,
        double locked,
        double free,
        double initialMargin,
        double maintenanceMargin,
        double unrealizedPnl,
        double equity,
        boolean marginCall,
        boolean liquidationRequired,
        long lifecycleSequence) {
    public ReplayAccountState {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }
}
