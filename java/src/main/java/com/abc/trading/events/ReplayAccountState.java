package com.abc.trading.events;

import java.math.BigDecimal;
import java.util.Objects;

/** Reconstructed account snapshot derived from canonical persisted events. */
public final class ReplayAccountState {
    private final String currency;
    private final BigDecimal totalDecimal;
    private final BigDecimal lockedDecimal;
    private final BigDecimal freeDecimal;
    private final BigDecimal initialMarginDecimal;
    private final BigDecimal maintenanceMarginDecimal;
    private final BigDecimal unrealizedPnlDecimal;
    private final BigDecimal equityDecimal;
    private final boolean marginCall;
    private final boolean liquidationRequired;
    private final long lifecycleSequence;

    public ReplayAccountState(String currency, BigDecimal total, BigDecimal locked, BigDecimal free,
            BigDecimal initialMargin, BigDecimal maintenanceMargin, BigDecimal unrealizedPnl,
            BigDecimal equity, boolean marginCall, boolean liquidationRequired, long lifecycleSequence) {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        this.currency = currency;
        this.totalDecimal = Objects.requireNonNull(total, "total");
        this.lockedDecimal = Objects.requireNonNull(locked, "locked");
        this.freeDecimal = Objects.requireNonNull(free, "free");
        this.initialMarginDecimal = Objects.requireNonNull(initialMargin, "initialMargin");
        this.maintenanceMarginDecimal = Objects.requireNonNull(maintenanceMargin, "maintenanceMargin");
        this.unrealizedPnlDecimal = Objects.requireNonNull(unrealizedPnl, "unrealizedPnl");
        this.equityDecimal = Objects.requireNonNull(equity, "equity");
        this.marginCall = marginCall;
        this.liquidationRequired = liquidationRequired;
        this.lifecycleSequence = lifecycleSequence;
    }

    public ReplayAccountState(String currency, double total, double locked, double free,
            double initialMargin, double maintenanceMargin, double unrealizedPnl,
            double equity, boolean marginCall, boolean liquidationRequired, long lifecycleSequence) {
        this(currency, BigDecimal.valueOf(total), BigDecimal.valueOf(locked), BigDecimal.valueOf(free),
                BigDecimal.valueOf(initialMargin), BigDecimal.valueOf(maintenanceMargin),
                BigDecimal.valueOf(unrealizedPnl), BigDecimal.valueOf(equity), marginCall,
                liquidationRequired, lifecycleSequence);
    }

    public String currency() { return currency; }
    public double total() { return totalDecimal.doubleValue(); }
    public double locked() { return lockedDecimal.doubleValue(); }
    public double free() { return freeDecimal.doubleValue(); }
    public double initialMargin() { return initialMarginDecimal.doubleValue(); }
    public double maintenanceMargin() { return maintenanceMarginDecimal.doubleValue(); }
    public double unrealizedPnl() { return unrealizedPnlDecimal.doubleValue(); }
    public double equity() { return equityDecimal.doubleValue(); }
    public BigDecimal totalDecimal() { return totalDecimal; }
    public BigDecimal lockedDecimal() { return lockedDecimal; }
    public BigDecimal freeDecimal() { return freeDecimal; }
    public BigDecimal initialMarginDecimal() { return initialMarginDecimal; }
    public BigDecimal maintenanceMarginDecimal() { return maintenanceMarginDecimal; }
    public BigDecimal unrealizedPnlDecimal() { return unrealizedPnlDecimal; }
    public BigDecimal equityDecimal() { return equityDecimal; }
    public boolean marginCall() { return marginCall; }
    public boolean liquidationRequired() { return liquidationRequired; }
    public long lifecycleSequence() { return lifecycleSequence; }
}
