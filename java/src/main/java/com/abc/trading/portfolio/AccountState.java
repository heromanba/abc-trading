package com.abc.trading.portfolio;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable account balance and margin snapshot with exact decimal values. */
public final class AccountState {
    private final String venue;
    private final String currency;
    private final BigDecimal balanceTotalDecimal;
    private final BigDecimal balanceLockedDecimal;
    private final BigDecimal balanceFreeDecimal;
    private final BigDecimal marginInitialDecimal;
    private final BigDecimal marginMaintenanceDecimal;
    private final long tsInit;
    private final Map<String, AccountBalance> balances;
    private final BigDecimal unrealizedPnlDecimal;
    private final BigDecimal equityDecimal;
    private final boolean marginCall;
    private final boolean liquidationRequired;

    public AccountState(String venue, String currency, double balanceTotal, double balanceLocked,
            double balanceFree, double marginInitial, double marginMaintenance, long tsInit) {
        this(venue, currency, BigDecimal.valueOf(balanceTotal), BigDecimal.valueOf(balanceLocked),
                BigDecimal.valueOf(balanceFree), BigDecimal.valueOf(marginInitial),
                BigDecimal.valueOf(marginMaintenance), tsInit,
                Map.of(currency, new AccountBalance(currency, balanceTotal, balanceLocked, balanceFree)),
                BigDecimal.ZERO, BigDecimal.valueOf(balanceTotal), false, false);
    }

    public AccountState(String venue, String currency, double balanceTotal, double balanceLocked,
            double balanceFree, double marginInitial, double marginMaintenance, long tsInit,
            Map<String, AccountBalance> balances) {
        this(venue, currency, BigDecimal.valueOf(balanceTotal), BigDecimal.valueOf(balanceLocked),
                BigDecimal.valueOf(balanceFree), BigDecimal.valueOf(marginInitial),
                BigDecimal.valueOf(marginMaintenance), tsInit, balances, BigDecimal.ZERO,
                BigDecimal.valueOf(balanceTotal), false, false);
    }

    public AccountState(String venue, String currency, BigDecimal balanceTotal, BigDecimal balanceLocked,
            BigDecimal balanceFree, BigDecimal marginInitial, BigDecimal marginMaintenance, long tsInit,
            Map<String, AccountBalance> balances, BigDecimal unrealizedPnl, BigDecimal equity,
            boolean marginCall, boolean liquidationRequired) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        balanceTotalDecimal = Objects.requireNonNull(balanceTotal, "balanceTotal");
        balanceLockedDecimal = Objects.requireNonNull(balanceLocked, "balanceLocked");
        balanceFreeDecimal = Objects.requireNonNull(balanceFree, "balanceFree");
        marginInitialDecimal = Objects.requireNonNull(marginInitial, "marginInitial");
        marginMaintenanceDecimal = Objects.requireNonNull(marginMaintenance, "marginMaintenance");
        unrealizedPnlDecimal = Objects.requireNonNull(unrealizedPnl, "unrealizedPnl");
        equityDecimal = Objects.requireNonNull(equity, "equity");
        if (balanceLocked.signum() < 0 || marginInitial.signum() < 0 || marginMaintenance.signum() < 0) {
            throw new IllegalArgumentException("account values must be non-negative");
        }
        if (balanceTotal.compareTo(balanceLocked.add(balanceFree)) != 0) {
            throw new IllegalArgumentException("balanceTotal must equal balanceLocked + balanceFree");
        }
        if (equity.compareTo(balanceTotal.add(unrealizedPnl)) != 0) {
            throw new IllegalArgumentException("equity must equal balanceTotal + unrealizedPnl");
        }
        if (balances == null || balances.isEmpty()) throw new IllegalArgumentException("balances are required");
        this.venue = venue;
        this.currency = currency;
        this.tsInit = tsInit;
        this.balances = Map.copyOf(new LinkedHashMap<>(balances));
        this.marginCall = marginCall;
        this.liquidationRequired = liquidationRequired;
    }

    public String venue() { return venue; }
    public String currency() { return currency; }
    public double balanceTotal() { return balanceTotalDecimal.doubleValue(); }
    public double balanceLocked() { return balanceLockedDecimal.doubleValue(); }
    public double balanceFree() { return balanceFreeDecimal.doubleValue(); }
    public double marginInitial() { return marginInitialDecimal.doubleValue(); }
    public double marginMaintenance() { return marginMaintenanceDecimal.doubleValue(); }
    public long tsInit() { return tsInit; }
    public Map<String, AccountBalance> balances() { return balances; }
    public double unrealizedPnl() { return unrealizedPnlDecimal.doubleValue(); }
    public double equity() { return equityDecimal.doubleValue(); }
    public boolean marginCall() { return marginCall; }
    public boolean liquidationRequired() { return liquidationRequired; }
    public BigDecimal balanceTotalDecimal() { return balanceTotalDecimal; }
    public BigDecimal balanceLockedDecimal() { return balanceLockedDecimal; }
    public BigDecimal balanceFreeDecimal() { return balanceFreeDecimal; }
    public BigDecimal marginInitialDecimal() { return marginInitialDecimal; }
    public BigDecimal marginMaintenanceDecimal() { return marginMaintenanceDecimal; }
    public BigDecimal unrealizedPnlDecimal() { return unrealizedPnlDecimal; }
    public BigDecimal equityDecimal() { return equityDecimal; }
}
