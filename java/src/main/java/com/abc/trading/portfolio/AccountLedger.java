package com.abc.trading.portfolio;

import com.abc.trading.execution.OrderFill;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic single-currency margin ledger for simulated venues. */
public final class AccountLedger {
    private static final double DEFAULT_MAINTENANCE_RATE = 0.5;

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();

    public void configure(String venue, double startingBalance, String currency, double leverage) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (!Double.isFinite(startingBalance) || startingBalance < 0.0) {
            throw new IllegalArgumentException("startingBalance must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (!Double.isFinite(leverage) || leverage <= 0.0) throw new IllegalArgumentException("leverage must be positive");
        if (accounts.containsKey(venue)) throw new IllegalArgumentException("account already configured: " + venue);
        accounts.put(venue, new Account(venue, startingBalance, currency, leverage));
    }

    public boolean configured(String venue) {
        return accounts.containsKey(venue);
    }

    public boolean canReserve(String venue, int quantity, double price) {
        Account account = account(venue);
        if (account == null) return true;
        double required = margin(quantity, price, account.leverage);
        return account.total - account.locked(reservations) + 1e-9 >= required;
    }

    public void reserve(String venue, String orderId, int quantity, double price) {
        Account account = account(venue);
        if (account == null) return;
        if (!canReserve(venue, quantity, price)) {
            throw new IllegalArgumentException("insufficient available margin");
        }
        reservations.put(orderId, new Reservation(venue, margin(quantity, price, account.leverage)));
    }

    public void release(String orderId) {
        reservations.remove(orderId);
    }

    public void applyFill(String venue, OrderFill fill, double realizedPnlDelta) {
        Account account = account(venue);
        if (account == null) return;
        account.total += realizedPnlDelta;
        Reservation reservation = reservations.get(fill.orderId());
        if (reservation != null) {
            double released = margin(fill.quantity(), fill.price(), account.leverage);
            double remaining = reservation.margin - released;
            if (remaining <= 1e-9) reservations.remove(fill.orderId());
            else reservations.put(fill.orderId(), new Reservation(venue, remaining));
        }
    }

    public void updatePosition(String venue, String symbol, int position, double averagePrice, long timestamp) {
        Account account = account(venue);
        if (account == null) return;
        if (position == 0) account.positionMargins.remove(symbol);
        else account.positionMargins.put(symbol, margin(Math.abs(position), averagePrice, account.leverage));
        account.lastTimestamp = timestamp;
    }

    public AccountState state(String venue, long timestamp) {
        Account account = account(venue);
        if (account == null) return null;
        double locked = account.locked(reservations);
        double free = account.total - locked;
        if (free < 0.0 && free > -1e-9) free = 0.0;
        return new AccountState(venue, account.currency, account.total, locked, free,
                locked, locked * DEFAULT_MAINTENANCE_RATE, timestamp);
    }

    private Account account(String venue) {
        return accounts.get(venue);
    }

    private static double margin(int quantity, double price, double leverage) {
        return quantity * price / leverage;
    }

    private static final class Account {
        private final String venue;
        private final String currency;
        private final double leverage;
        private final Map<String, Double> positionMargins = new LinkedHashMap<>();
        private double total;
        private long lastTimestamp;

        private Account(String venue, double total, String currency, double leverage) {
            this.venue = venue;
            this.total = total;
            this.currency = currency;
            this.leverage = leverage;
        }

        private double locked(Map<String, Reservation> reservations) {
            double result = positionMargins.values().stream().mapToDouble(Double::doubleValue).sum();
            for (Reservation reservation : reservations.values()) {
                if (reservation.venue.equals(venue)) result += reservation.margin;
            }
            return result;
        }
    }

    private record Reservation(String venue, double margin) { }
}
