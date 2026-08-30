package com.abc.trading.portfolio;

import com.abc.trading.data.InstrumentSpec;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.data.Quantity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.math.MathContext;

/** Deterministic single-currency margin ledger for simulated venues. */
public final class AccountLedger {
    private static final double DEFAULT_MAINTENANCE_RATE = 0.5;
    private static final MathContext DECIMAL_CONTEXT = MathContext.DECIMAL128;

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    private final Map<String, BigDecimal> fxRates = new LinkedHashMap<>();

    public void configure(String venue, double startingBalance, String currency, double leverage) {
        configure(venue, BigDecimal.valueOf(startingBalance), currency, BigDecimal.valueOf(leverage), AccountType.MARGIN);
        }

        public void configure(String venue, double startingBalance, String currency, double leverage,
            AccountType accountType) {
        configure(venue, BigDecimal.valueOf(startingBalance), currency, BigDecimal.valueOf(leverage), accountType);
        }

        public void configure(String venue, BigDecimal startingBalance, String currency, BigDecimal leverage,
            AccountType accountType) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (startingBalance == null || startingBalance.signum() < 0) {
            throw new IllegalArgumentException("startingBalance must be finite and non-negative");
        }
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (leverage == null || leverage.signum() <= 0) throw new IllegalArgumentException("leverage must be positive");
        if (accountType == null) throw new IllegalArgumentException("accountType is required");
        if (accounts.containsKey(venue)) throw new IllegalArgumentException("account already configured: " + venue);
        accounts.put(venue, new Account(venue, startingBalance, currency, leverage, accountType));
    }

    public boolean configured(String venue) {
        return accounts.containsKey(venue);
    }

    public void deposit(String venue, String currency, double amount) {
        deposit(venue, currency, BigDecimal.valueOf(amount));
    }

    public void deposit(String venue, String currency, BigDecimal amount) {
        Account account = account(venue);
        if (account == null) throw new IllegalArgumentException("account is not configured: " + venue);
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("amount must be non-negative");
        account.totals.merge(currency, amount, BigDecimal::add);
    }

    public void setFxRate(String fromCurrency, String toCurrency, double rate) {
        setFxRate(fromCurrency, toCurrency, BigDecimal.valueOf(rate));
    }

    public void setFxRate(String fromCurrency, String toCurrency, BigDecimal rate) {
        if (fromCurrency == null || fromCurrency.isBlank() || toCurrency == null || toCurrency.isBlank()) {
            throw new IllegalArgumentException("currencies are required");
        }
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("rate must be positive");
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
        return canReserve(venue, Quantity.fromInt(quantity), price, instrument, side, BigDecimal.valueOf(position));
        }

        public boolean canReserve(String venue, Quantity quantity, double price, InstrumentSpec instrument,
            SignalDirection side, BigDecimal position) {
        Account account = account(venue);
        if (account == null) return true;
        if (instrument != null) instrument.validateQuantity(quantity);
        if (instrument != null && price > 0.0) instrument.validatePrice(price);
        if (account.type == AccountType.CASH && side == SignalDirection.SELL
            && position.compareTo(quantity.asDecimal()) < 0) return false;
        if (account.type == AccountType.MARGIN && increasesExposure(side, position)
            && isMaintenanceBreached(account)) return false;
        BigDecimal required = requiredMargin(quantity, price, account, instrument, side, position);
        String currency = instrument == null ? account.currency : marginCurrency(instrument);
        String availableCurrency = account.totals.containsKey(currency) ? currency : account.currency;
        BigDecimal available = account.totals.getOrDefault(availableCurrency, BigDecimal.ZERO)
            .subtract(account.locked(reservations, availableCurrency, this));
        BigDecimal requiredInAvailable = convert(required, currency, availableCurrency);
        return requiredInAvailable != null && available.compareTo(requiredInAvailable) >= 0;
    }

    private boolean canReserve(String venue, Quantity quantity, double price, InstrumentSpec instrument,
            SignalDirection side, int position) {
        return canReserve(venue, quantity, price, instrument, side, BigDecimal.valueOf(position));
    }

    public void reserve(String venue, String orderId, int quantity, double price) {
        reserve(venue, orderId, Quantity.fromInt(quantity), price, null, SignalDirection.BUY, Integer.MAX_VALUE);
    }

    public void reserve(String venue, String orderId, int quantity, double price, InstrumentSpec instrument,
            SignalDirection side, int position) {
        reserve(venue, orderId, Quantity.fromInt(quantity), price, instrument, side, BigDecimal.valueOf(position));
        }

        public void reserve(String venue, String orderId, Quantity quantity, double price, InstrumentSpec instrument,
                SignalDirection side, BigDecimal position) {
        Account account = account(venue);
        if (account == null) return;
        if (!canReserve(venue, quantity, price, instrument, side, position)) {
            throw new IllegalArgumentException("insufficient available margin");
        }
        String currency = instrument == null ? account.currency : marginCurrency(instrument);
        reservations.put(orderId, new Reservation(venue, currency,
            quantity, requiredMargin(quantity, price, account, instrument, side, position)));
    }

    private void reserve(String venue, String orderId, Quantity quantity, double price,
            InstrumentSpec instrument, SignalDirection side, int position) {
        reserve(venue, orderId, quantity, price, instrument, side, BigDecimal.valueOf(position));
    }

    public void release(String orderId) {
        reservations.remove(orderId);
    }

    public void applyFill(String venue, OrderFill fill, double realizedPnlDelta) {
        applyFill(venue, fill, BigDecimal.valueOf(realizedPnlDelta), null);
    }

    public void applyFill(String venue, OrderFill fill, double realizedPnlDelta, InstrumentSpec instrument) {
        applyFill(venue, fill, BigDecimal.valueOf(realizedPnlDelta), instrument);
    }

    public void applyFill(String venue, OrderFill fill, BigDecimal realizedPnlDelta, InstrumentSpec instrument) {
        Account account = account(venue);
        if (account == null) return;
        String currency = instrument == null ? account.currency : marginCurrency(instrument);
        if (account.type == AccountType.CASH) {
            BigDecimal notional = fill.quantity().asDecimal().multiply(BigDecimal.valueOf(fill.price()), DECIMAL_CONTEXT);
            BigDecimal cashDelta = fill.side() == SignalDirection.BUY ? notional.negate() : notional;
            BigDecimal commission = convert(fill.commission().amountDecimal(), fill.commission().currency(), currency);
            if (commission == null) return;
            String settlementCurrency = account.totals.containsKey(currency) ? currency : account.currency;
            BigDecimal settledCash = convert(cashDelta, currency, settlementCurrency);
            if (settledCash == null) return;
            account.totals.merge(settlementCurrency, settledCash.subtract(commission), BigDecimal::add);
        } else {
            BigDecimal realized = convert(realizedPnlDelta.add(fill.commission().amountDecimal()), currency, account.currency);
            BigDecimal commission = convert(fill.commission().amountDecimal(), fill.commission().currency(), account.currency);
            if (realized == null || commission == null) return;
            account.totals.merge(account.currency, realized.subtract(commission), BigDecimal::add);
        }
        Reservation reservation = reservations.get(fill.orderId());
        if (reservation != null) {
            BigDecimal released = reservation.margin.multiply(fill.quantity().asDecimal(), DECIMAL_CONTEXT)
                    .divide(reservation.quantity.asDecimal(), DECIMAL_CONTEXT);
            BigDecimal remaining = reservation.margin.subtract(released);
            if (remaining.signum() <= 0) reservations.remove(fill.orderId());
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
        updatePosition(venue, instrument, BigDecimal.valueOf(position), averagePrice, timestamp);
    }

    public void updatePosition(String venue, InstrumentSpec instrument, BigDecimal position,
            double averagePrice, long timestamp) {
        Account account = account(venue);
        if (account == null) return;
        if (position.signum() == 0) account.positionMargins.remove(instrument.symbol());
        else account.positionMargins.put(instrument.symbol(), new MarginRequirement(
            instrument.symbol(), marginCurrency(instrument), position, averagePrice, averagePrice,
            account.type == AccountType.CASH ? BigDecimal.ZERO : marginFor(instrument, Quantity.fromDecimal(position.abs(), position.scale()), averagePrice,
                account.leverage, false),
            account.type == AccountType.CASH ? BigDecimal.ZERO : marginFor(instrument, Quantity.fromDecimal(position.abs(), position.scale()), averagePrice,
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
            BigDecimal total = account.totals.get(currency);
            BigDecimal locked = account.locked(reservations, currency, this);
            balances.put(currency, new AccountBalance(currency, total, locked, total.subtract(locked)));
        }
        AccountBalance primary = balances.get(account.currency);
        BigDecimal locked = primary.lockedDecimal();
        BigDecimal free = primary.freeDecimal();
        BigDecimal initial = account.initialMargin(account.currency, reservations, this);
        BigDecimal maintenance = account.maintenanceMargin(account.currency, this);
        BigDecimal unrealized = account.unrealized(account.currency, this);
        BigDecimal equity = primary.totalDecimal().add(unrealized);
        boolean marginCall = maintenance.signum() > 0 && equity.compareTo(maintenance) < 0;
        boolean liquidationRequired = marginCall;
        return new AccountState(venue, account.currency,
            primary.totalDecimal(), locked, free, initial, maintenance, timestamp, balances,
            unrealized, equity, marginCall, liquidationRequired);
    }

    private Account account(String venue) {
        return accounts.get(venue);
    }

    private BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount.signum() == 0 || fromCurrency.equals(toCurrency)) return amount;
        BigDecimal direct = fxRates.get(fromCurrency + "->" + toCurrency);
        if (direct != null) return amount.multiply(direct, DECIMAL_CONTEXT);
        BigDecimal inverse = fxRates.get(toCurrency + "->" + fromCurrency);
        if (inverse != null) return amount.divide(inverse, DECIMAL_CONTEXT);
        return null;
    }

    private static boolean increasesExposure(SignalDirection side, BigDecimal position) {
        return position.signum() == 0
                || position.signum() > 0 && side == SignalDirection.BUY
                || position.signum() < 0 && side == SignalDirection.SELL;
    }

    private boolean isMaintenanceBreached(Account account) {
        BigDecimal maintenance = account.maintenanceMargin(account.currency, this);
        BigDecimal unrealized = account.unrealized(account.currency, this);
        BigDecimal equity = account.totals.getOrDefault(account.currency, BigDecimal.ZERO).add(unrealized);
        return maintenance.signum() > 0 && equity.compareTo(maintenance) < 0;
    }

    private static BigDecimal margin(Quantity quantity, double price, BigDecimal leverage, double rate) {
        return quantity.asDecimal().multiply(BigDecimal.valueOf(price), DECIMAL_CONTEXT)
                .multiply(BigDecimal.valueOf(rate), DECIMAL_CONTEXT).divide(leverage, DECIMAL_CONTEXT);
    }

    private static BigDecimal requiredMargin(Quantity quantity, double price, Account account,
            InstrumentSpec instrument, SignalDirection side, BigDecimal position) {
        if (account.type == AccountType.CASH && side == SignalDirection.SELL) return BigDecimal.ZERO;
        if (account.type == AccountType.MARGIN && position.signum() != 0
                && position.signum() != (side == SignalDirection.BUY ? 1 : -1)) {
            BigDecimal positionMagnitude = position.abs();
            if (quantity.asDecimal().compareTo(positionMagnitude) <= 0) return BigDecimal.ZERO;
            quantity = Quantity.fromDecimal(quantity.asDecimal().subtract(positionMagnitude), quantity.precision());
            if (quantity.isZero()) return BigDecimal.ZERO;
        }

        return instrument == null ? margin(quantity, price, account.leverage, 1.0)
            : marginFor(instrument, quantity, price, account.leverage, false);
        }

    private static String marginCurrency(InstrumentSpec instrument) {
        return instrument.marginModelType() == com.abc.trading.data.MarginModelType.INVERSE_NOTIONAL_RATE
                ? instrument.baseCurrency() : instrument.quoteCurrency();
    }

        private static BigDecimal marginFor(InstrumentSpec instrument, Quantity quantity, double price,
            BigDecimal leverage, boolean maintenance) {
        return switch (instrument.marginModelType()) {
            case NOTIONAL_RATE -> margin(quantity, price, leverage,
                maintenance ? instrument.marginMaintenanceRate() : instrument.marginInitialRate());
            case STANDARD_NOTIONAL_RATE -> margin(quantity, price, BigDecimal.ONE,
                maintenance ? instrument.marginMaintenanceRate() : instrument.marginInitialRate());
            case INVERSE_NOTIONAL_RATE -> {
                if (price <= 0.0) throw new IllegalArgumentException("price must be positive");
                double rate = maintenance ? instrument.marginMaintenanceRate() : instrument.marginInitialRate();
                yield quantity.asDecimal().divide(BigDecimal.valueOf(price), DECIMAL_CONTEXT)
                    .multiply(BigDecimal.valueOf(rate), DECIMAL_CONTEXT).divide(leverage, DECIMAL_CONTEXT);
            }
            case FIXED_PER_UNIT -> quantity.asDecimal()
                .multiply(BigDecimal.valueOf(maintenance
                    ? instrument.maintenanceMarginPerUnit() : instrument.initialMarginPerUnit()), DECIMAL_CONTEXT)
                .divide(leverage, DECIMAL_CONTEXT);
        };
    }

    private static final class Account {
        private final String venue;
        private final String currency;
        private final BigDecimal leverage;
        private final AccountType type;
        private final Map<String, BigDecimal> totals = new LinkedHashMap<>();
        private final Map<String, MarginRequirement> positionMargins = new LinkedHashMap<>();
        private Account(String venue, BigDecimal total, String currency, BigDecimal leverage, AccountType type) {
            this.venue = venue;
            this.currency = currency;
            this.leverage = leverage;
            this.type = type;
            this.totals.put(currency, total);
        }

        private BigDecimal locked(Map<String, Reservation> reservations, String currency, AccountLedger ledger) {
            BigDecimal result = positionMargins.values().stream()
                .map(margin -> ledger.convert(margin.maintenance, margin.currency, currency))
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Reservation reservation : reservations.values()) {
                if (reservation.venue.equals(venue)) {
                    BigDecimal converted = ledger == null || reservation.currency.equals(currency)
                            ? reservation.margin : ledger.convert(reservation.margin, reservation.currency, currency);
                    if (converted != null) result = result.add(converted);
                }
            }
            return result;
        }

        private BigDecimal initialMargin(String currency, Map<String, Reservation> reservations, AccountLedger ledger) {
            BigDecimal result = BigDecimal.ZERO;
            for (Reservation reservation : reservations.values()) {
                if (reservation.venue.equals(venue)) {
                    BigDecimal converted = ledger.convert(reservation.margin, reservation.currency, currency);
                    if (converted != null) result = result.add(converted);
                }
            }
            return result;
        }

        private BigDecimal maintenanceMargin(String currency, AccountLedger ledger) {
            return positionMargins.values().stream()
                    .map(margin -> ledger.convert(margin.maintenance, margin.currency, currency))
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal unrealized(String currency, AccountLedger ledger) {
                    return positionMargins.values().stream()
                        .map(position -> ledger.convert(
                            position.inverse
                                ? position.quantity.multiply(BigDecimal.ONE.divide(BigDecimal.valueOf(position.averagePrice), DECIMAL_CONTEXT)
                                    .subtract(BigDecimal.ONE.divide(BigDecimal.valueOf(position.markPrice), DECIMAL_CONTEXT)), DECIMAL_CONTEXT)
                                : BigDecimal.valueOf(position.markPrice - position.averagePrice).multiply(position.quantity, DECIMAL_CONTEXT),
                            position.currency, currency))
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                }
    }

    private record Reservation(String venue, String currency, Quantity quantity, BigDecimal margin) { }
        private record MarginRequirement(String symbol, String currency, BigDecimal quantity, double averagePrice,
            double markPrice, BigDecimal initial, BigDecimal maintenance, boolean inverse) { }
}
