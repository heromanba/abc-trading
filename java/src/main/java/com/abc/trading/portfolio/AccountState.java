package com.abc.trading.portfolio;

/** Immutable account balance and margin snapshot. */
public record AccountState(
        String venue,
        String currency,
        double balanceTotal,
        double balanceLocked,
        double balanceFree,
        double marginInitial,
        double marginMaintenance,
        long tsInit) {
    public AccountState {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        validateFinite(balanceTotal, "balanceTotal");
        validateNonNegative(balanceLocked, "balanceLocked");
        validateFinite(balanceFree, "balanceFree");
        validateNonNegative(marginInitial, "marginInitial");
        validateNonNegative(marginMaintenance, "marginMaintenance");
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
