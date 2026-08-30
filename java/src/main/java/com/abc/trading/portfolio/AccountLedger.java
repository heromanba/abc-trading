package com.abc.trading.portfolio;

import com.abc.trading.data.InstrumentSpec;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.data.Quantity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic single-currency margin ledger for simulated venues. */
public final class AccountLedger {
    private static final double DEFAULT_MAINTENANCE_RATE = 0.5;

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    private final Map<String, Double> fxRates = new LinkedHashMap<>();

    public void configure(String venue, double startingBalance, String currency, double leverage) {
        configure(venue, startingBalance, currency, leverage, AccountType.MARGIN);
        }

        public void configure(String venue, double startingBalance, String currency, double leverage,
            AccountType accountType) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (!Double.isFinite(startingBalance) || startingBalance < 0.0) {
            throw new IllegalArgumentException("startingBalance must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (!Double.isFinite(leverage) || leverage <= 0.0) throw new IllegalArgumentException("leverage must be positive");
        if (accountType == null) throw new IllegalArgumentException("accountType is required");
        if (accounts.containsKey(venue)) throw new IllegalArgumentException("account already configured: " + venue);
        accounts.put(venue, new Account(venue, startingBalance, currency, leverage, accountType));
    }

    public boolean configured(String venue) {
        return accounts.containsKey(venue);
    }

    public void deposit(String venue, String currency, double amount) {
        Account account = account(venue);
        if (account == null) throw new IllegalArgumentException("account is not configured: " + venue);
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (!Double.isFinite(amount) || amount < 0.0) throw new IllegalArgumentException("amount must be non-negative");
        account.totals.merge(currency, amount, Double::sum);
    }

    public void setFxRate(String fromCurrency, String toCurrency, double rate) {
        if (fromCurrency == null || fromCurrency.isBlank() || toCurrency == null || toCurrency.isBlank()) {
            throw new IllegalArgumentException("currencies are required");
        }
        if (!Double.isFinite(rate) || rate <= 0.0) throw new IllegalArgumentException("rate must be positive");
        fxRates.put(fromCurrency + "->" + toCurrency, rate);
    }

    public void applyFxRate(FxRateUpdate update) {
        setFxRate(update.fromCurrency(), update.toCurrency(), update.rate());
    }

    public Map<String, AccountState> states(long timestamp) {
        Map<String, AccountState> states = new LinkedHashMap<>();
        for (String venue : accounts.keySet()) states.put(venue, state(venue, timestamp));
        return Map.copyOf(states);
    }

    public boolean canReserve(String venue, int quantity, double price) {
        return canReserve(venue, Quantity.fromInt(quantity), price, null, SignalDirection.BUY, Integer.MAX_VALUE);
    }

    public boolean canReserve(String venue, int quantity, double price, InstrumentSpec instrument,
            SignalDirection side, int position) {
        return canReserve(venue, Quantity.fromInt(quantity), price, instrument, side, position);
        }

        public boolean canReserve(String venue, Quantity quantity, double price, InstrumentSpec instrument,
            SignalDirection side, int position) {
        Account account = account(venue);
        if (account == null) return true;
        if (account.type == AccountType.CASH && side == SignalDirection.SELL && position < quantity.asDouble()) return false;
        if (account.type == AccountType.MARGIN && increasesExposure(side, position)
            && isMaintenanceBreached(account)) return false;
        double required = requiredMargin(quantity, price, account, instrument, side, position);
        String currency = instrument == null ? account.currency : marginCurrency(instrument);
        String availableCurrency = account.totals.containsKey(currency) ? currency : account.currency;
        double available = account.totals.getOrDefault(availableCurrency, 0.0)
            - account.locked(reservations, availableCurrency, this);
        double requiredInAvailable = convert(required, currency, availableCurrency);
        return Double.isFinite(requiredInAvailable) && available + 1e-9 >= requiredInAvailable;
    }

    public void reserve(String venue, String orderId, int quantity, double price) {
        reserve(venue, orderId, Quantity.fromInt(quantity), price, null, SignalDirection.BUY, Integer.MAX_VALUE);
    }

    public void reserve(String venue, String orderId, int quantity, double price, InstrumentSpec instrument,
            SignalDirection side, int position) {
        reserve(venue, orderId, Quantity.fromInt(quantity), price, instrument, side, position);
        }

        public void reserve(String venue, String orderId, Quantity quantity, double price, InstrumentSpec instrument,
            SignalDirection side, int position) {
        Account account = account(venue);
        if (account == null) return;
        if (!canReserve(venue, quantity, price, instrument, side, position)) {
            throw new IllegalArgumentException("insufficient available margin");
        }
        String currency = instrument == null ? account.currency : marginCurrency(instrument);
        reservations.put(orderId, new Reservation(venue, currency,
            quantity, requiredMargin(quantity, price, account, instrument, side, position)));
    }

    public void release(String orderId) {
        reservations.remove(orderId);
    }

    public void applyFill(String venue, OrderFill fill, double realizedPnlDelta) {
        applyFill(venue, fill, realizedPnlDelta, null);
    }

    public void applyFill(String venue, OrderFill fill, double realizedPnlDelta, InstrumentSpec instrument) {
        Account account = account(venue);
        if (account == null) return;
        String currency = instrument == null ? account.currency : marginCurrency(instrument);
        if (account.type == AccountType.CASH) {
            double notional = fill.quantity().asDouble() * fill.price();
            double cashDelta = fill.side() == SignalDirection.BUY ? -notional : notional;
            double commission = convert(fill.commission().amount(), fill.commission().currency(), currency);
            if (!Double.isFinite(commission)) return;
            String settlementCurrency = account.totals.containsKey(currency) ? currency : account.currency;
            double settledCash = convert(cashDelta, currency, settlementCurrency);
            if (!Double.isFinite(settledCash)) return;
            account.totals.merge(settlementCurrency, settledCash - commission, Double::sum);
        } else {
            double realized = convert(realizedPnlDelta + fill.commission().amount(), currency, account.currency);
            double commission = convert(fill.commission().amount(), fill.commission().currency(), account.currency);
            if (!Double.isFinite(realized) || !Double.isFinite(commission)) return;
            account.totals.merge(account.currency, realized - commission, Double::sum);
        }
        Reservation reservation = reservations.get(fill.orderId());
        if (reservation != null) {
            double released = reservation.margin * fill.quantity().asDouble() / reservation.quantity.asDouble();
            double remaining = reservation.margin - released;
            if (remaining <= 1e-9) reservations.remove(fill.orderId());
            else reservations.put(fill.orderId(), new Reservation(venue, reservation.currency,
                    reservation.quantity.subtract(fill.quantity()), remaining));
        }
    }

    public void updatePosition(String venue, String symbol, int position, double averagePrice, long timestamp) {
        updatePosition(venue, new InstrumentSpec(symbol, venue, com.abc.trading.data.TickScheme.fixed(0.01),
                symbol, "USD", 1.0, DEFAULT_MAINTENANCE_RATE), position, averagePrice, timestamp);
    }

    public void updatePosition(String venue, InstrumentSpec instrument, int position,
            double averagePrice, long timestamp) {
        Account account = account(venue);
        if (account == null) return;
        if (position == 0) account.positionMargins.remove(instrument.symbol());
        else account.positionMargins.put(instrument.symbol(), new MarginRequirement(
            instrument.symbol(), marginCurrency(instrument), position, averagePrice, averagePrice,
            account.type == AccountType.CASH ? 0.0 : marginFor(instrument, Quantity.fromInt(Math.abs(position)), averagePrice,
                account.leverage, false),
            account.type == AccountType.CASH ? 0.0 : marginFor(instrument, Quantity.fromInt(Math.abs(position)), averagePrice,
                account.leverage, true),
            instrument.marginModelType() == com.abc.trading.data.MarginModelType.INVERSE_NOTIONAL_RATE));
        }

        public void updateMarketPrice(String venue, InstrumentSpec instrument, double markPrice, long timestamp) {
        Account account = account(venue);
        if (account == null) return;
        MarginRequirement position = account.positionMargins.get(instrument.symbol());
        if (position != null) {
            account.positionMargins.put(instrument.symbol(), new MarginRequirement(
                position.symbol, position.currency, position.quantity, position.averagePrice,
                markPrice, position.initial, position.maintenance, position.inverse));
        }
    }

    public AccountState state(String venue, long timestamp) {
        Account account = account(venue);
        if (account == null) return null;
        Map<String, AccountBalance> balances = new LinkedHashMap<>();
        for (String currency : account.totals.keySet()) {
            double total = account.totals.get(currency);
                double locked = account.locked(reservations, currency, this);
            balances.put(currency, new AccountBalance(currency, total, locked, total - locked));
        }
        AccountBalance primary = balances.get(account.currency);
        double locked = primary.locked();
        double free = primary.free();
        if (free < 0.0 && free > -1e-9) free = 0.0;
        double initial = account.initialMargin(account.currency, reservations, this);
        double maintenance = account.maintenanceMargin(account.currency, this);
        double unrealized = account.unrealized(account.currency, this);
        double equity = primary.total() + unrealized;
        boolean marginCall = maintenance > 0.0 && equity < maintenance;
        boolean liquidationRequired = marginCall;
        return new AccountState(venue, account.currency, primary.total(), locked, free,
            initial, maintenance, timestamp, balances, unrealized, equity, marginCall, liquidationRequired);
    }

    private Account account(String venue) {
        return accounts.get(venue);
    }

    private double convert(double amount, String fromCurrency, String toCurrency) {
        if (amount == 0.0) return 0.0;
        if (fromCurrency.equals(toCurrency)) return amount;
        Double direct = fxRates.get(fromCurrency + "->" + toCurrency);
        if (direct != null) return amount * direct;
        Double inverse = fxRates.get(toCurrency + "->" + fromCurrency);
        if (inverse != null) return amount / inverse;
        return Double.NaN;
    }

    private static boolean increasesExposure(SignalDirection side, int position) {
        return position == 0
                || position > 0 && side == SignalDirection.BUY
                || position < 0 && side == SignalDirection.SELL;
    }

    private boolean isMaintenanceBreached(Account account) {
        double maintenance = account.maintenanceMargin(account.currency, this);
        double unrealized = account.unrealized(account.currency, this);
        double equity = account.totals.getOrDefault(account.currency, 0.0) + unrealized;
        return maintenance > 0.0 && equity < maintenance;
    }

    private static double margin(Quantity quantity, double price, double leverage, double rate) {
        return quantity.asDouble() * price * rate / leverage;
    }

    private static double requiredMargin(Quantity quantity, double price, Account account,
            InstrumentSpec instrument, SignalDirection side, int position) {
        if (account.type == AccountType.CASH && side == SignalDirection.SELL) return 0.0;
        if (account.type == AccountType.MARGIN && position != 0
                && Integer.signum(position) != Integer.signum(side == SignalDirection.BUY ? 1 : -1)) {
            quantity = Quantity.fromDecimal(quantity.asDecimal().subtract(java.math.BigDecimal.valueOf(Math.abs(position))), quantity.precision());
            if (quantity.isZero()) return 0.0;
        }

        return instrument == null ? margin(quantity, price, account.leverage, 1.0)
            : marginFor(instrument, quantity, price, account.leverage, false);
        }

    private static String marginCurrency(InstrumentSpec instrument) {
        return instrument.marginModelType() == com.abc.trading.data.MarginModelType.INVERSE_NOTIONAL_RATE
                ? instrument.baseCurrency() : instrument.quoteCurrency();
    }

        private static double marginFor(InstrumentSpec instrument, Quantity quantity, double price,
            double leverage, boolean maintenance) {
        return switch (instrument.marginModelType()) {
            case NOTIONAL_RATE -> margin(quantity, price, leverage,
                maintenance ? instrument.marginMaintenanceRate() : instrument.marginInitialRate());
            case STANDARD_NOTIONAL_RATE -> margin(quantity, price, 1.0,
                maintenance ? instrument.marginMaintenanceRate() : instrument.marginInitialRate());
            case INVERSE_NOTIONAL_RATE -> {
                if (price <= 0.0) throw new IllegalArgumentException("price must be positive");
                double rate = maintenance ? instrument.marginMaintenanceRate() : instrument.marginInitialRate();
                yield quantity.asDouble() / price * rate / leverage;
            }
            case FIXED_PER_UNIT -> quantity.asDouble() * (maintenance
                ? instrument.maintenanceMarginPerUnit() : instrument.initialMarginPerUnit()) / leverage;
        };
    }

    private static final class Account {
        private final String venue;
        private final String currency;
        private final double leverage;
        private final AccountType type;
        private final Map<String, Double> totals = new LinkedHashMap<>();
        private final Map<String, MarginRequirement> positionMargins = new LinkedHashMap<>();
        private Account(String venue, double total, String currency, double leverage, AccountType type) {
            this.venue = venue;
            this.currency = currency;
            this.leverage = leverage;
            this.type = type;
            this.totals.put(currency, total);
        }

        private double locked(Map<String, Reservation> reservations, String currency, AccountLedger ledger) {
            double result = positionMargins.values().stream()
                .mapToDouble(margin -> ledger.convert(margin.maintenance, margin.currency, currency)).sum();
            for (Reservation reservation : reservations.values()) {
                if (reservation.venue.equals(venue)) {
                    result += ledger == null || reservation.currency.equals(currency)
                            ? reservation.margin : ledger.convert(reservation.margin, reservation.currency, currency);
                }
            }
            return result;
        }

        private double initialMargin(String currency, Map<String, Reservation> reservations, AccountLedger ledger) {
            double result = 0.0;
            for (Reservation reservation : reservations.values()) {
                if (reservation.venue.equals(venue)) {
                    result += ledger.convert(reservation.margin, reservation.currency, currency);
                }
            }
            return result;
        }

        private double maintenanceMargin(String currency, AccountLedger ledger) {
            return positionMargins.values().stream()
                    .mapToDouble(margin -> ledger.convert(margin.maintenance, margin.currency, currency)).sum();
        }

        private double unrealized(String currency, AccountLedger ledger) {
                    return positionMargins.values().stream()
                        .mapToDouble(position -> ledger.convert(
                            position.inverse
                                ? position.quantity * (1.0 / position.averagePrice - 1.0 / position.markPrice)
                                : (position.markPrice - position.averagePrice) * position.quantity,
                            position.currency, currency)).sum();
                }
    }

    private record Reservation(String venue, String currency, Quantity quantity, double margin) { }
        private record MarginRequirement(String symbol, String currency, int quantity, double averagePrice,
            double markPrice, double initial, double maintenance, boolean inverse) { }
}
