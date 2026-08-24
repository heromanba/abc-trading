package com.abc.trading.portfolio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable account balance and margin snapshot. */
public record AccountState(
        String venue,
        String currency,
        double balanceTotal,
        double balanceLocked,
        double balanceFree,
        double marginInitial,
        double marginMaintenance,
        long tsInit,
        Map<String, AccountBalance> balances,
        double unrealizedPnl,
        double equity,
        boolean marginCall,
        boolean liquidationRequired) {
    public AccountState(
            String venue, String currency, double balanceTotal, double balanceLocked,
            double balanceFree, double marginInitial, double marginMaintenance, long tsInit) {
        this(venue, currency, balanceTotal, balanceLocked, balanceFree, marginInitial,
                marginMaintenance, tsInit,
            Map.of(currency, new AccountBalance(currency, balanceTotal, balanceLocked, balanceFree)),
            0.0, balanceTotal, false, false);
        }

        public AccountState(
            String venue, String currency, double balanceTotal, double balanceLocked,
            double balanceFree, double marginInitial, double marginMaintenance, long tsInit,
            Map<String, AccountBalance> balances) {
        this(venue, currency, balanceTotal, balanceLocked, balanceFree, marginInitial,
            marginMaintenance, tsInit, balances, 0.0, balanceTotal, false, false);
    }

    public AccountState {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        validateFinite(balanceTotal, "balanceTotal");
        validateNonNegative(balanceLocked, "balanceLocked");
        validateFinite(balanceFree, "balanceFree");
        validateNonNegative(marginInitial, "marginInitial");
        validateNonNegative(marginMaintenance, "marginMaintenance");
        validateFinite(unrealizedPnl, "unrealizedPnl");
        validateFinite(equity, "equity");
        if (Math.abs(equity - balanceTotal - unrealizedPnl) > 1e-9) {
            throw new IllegalArgumentException("equity must equal balanceTotal + unrealizedPnl");
        }
        if (balances == null || balances.isEmpty()) throw new IllegalArgumentException("balances are required");
        balances = Map.copyOf(new LinkedHashMap<>(balances));
        if (Math.abs(balanceTotal - balanceLocked - balanceFree) > 1e-9) {
            throw new IllegalArgumentException("balanceTotal must equal balanceLocked + balanceFree");
        }
    }

    private static void validateFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void validateNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
