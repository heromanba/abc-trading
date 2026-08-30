package com.abc.trading.portfolio;

import com.abc.trading.cache.Cache;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.data.MarketDataSnapshot;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal deterministic portfolio state owner. */
public final class Portfolio {
    private final Cache cache;
    private final Map<String, Double> averagePrices = new LinkedHashMap<>();
    private final Map<String, BigDecimal> realizedPnl = new LinkedHashMap<>();
    private final AccountLedger accountLedger = new AccountLedger();

    public Portfolio(Cache cache) {
        this.cache = cache;
    }

    public void applyOrderIntent(OrderIntent order) {
        accountLedger.reserve(cache.venue(order.symbol()), order.orderId(), order.quantity(), order.price(),
            cache.instrument(order.symbol()), order.side(), cache.position(order.symbol()));
        cache.recordOrder(order);
    }

    public void applyLimitOrderIntent(LimitOrderIntent order) {
        accountLedger.reserve(cache.venue(order.symbol()), order.orderId(), order.quantity(), order.limitPrice(),
            cache.instrument(order.symbol()), order.side(), cache.position(order.symbol()));
    }

    public AccountState accountStateForSymbol(String symbol, long timestamp) {
        return accountState(cache.venue(symbol), timestamp);
    }

    public PositionUpdate applyFill(OrderFill fill) {
        BigDecimal previousPosition = cache.position(fill.symbol());
        double previousAverage = averagePrices.getOrDefault(fill.symbol(), 0.0);
        BigDecimal signedQuantity = fill.quantity().asDecimal();
        if (fill.side() == com.abc.trading.execution.SignalDirection.SELL) signedQuantity = signedQuantity.negate();
        BigDecimal nextPosition = previousPosition.add(signedQuantity);
        BigDecimal realizedPnlDelta = fill.commission().amountDecimal().negate();

        if (previousPosition.signum() != 0 && previousPosition.signum() != signedQuantity.signum()) {
            BigDecimal closedQuantity = previousPosition.abs().min(signedQuantity.abs());
                BigDecimal direction = previousPosition.signum() > 0 ? BigDecimal.ONE : BigDecimal.ONE.negate();
                realizedPnlDelta = realizedPnlDelta.add(
                    BigDecimal.valueOf(fill.price()).subtract(BigDecimal.valueOf(previousAverage))
                        .multiply(closedQuantity, java.math.MathContext.DECIMAL128).multiply(direction));
        }

        if (nextPosition.signum() == 0) {
            averagePrices.put(fill.symbol(), 0.0);
        } else if (previousPosition.signum() == 0
                || previousPosition.signum() == signedQuantity.signum()) {
            double total = previousPosition.abs().doubleValue() * previousAverage
                    + signedQuantity.abs().doubleValue() * fill.price();
            averagePrices.put(fill.symbol(), total / nextPosition.abs().doubleValue());
        } else {
            averagePrices.put(fill.symbol(), fill.price());
        }

        BigDecimal cumulativeRealizedPnl = realizedPnl.getOrDefault(fill.symbol(), BigDecimal.ZERO).add(realizedPnlDelta);
        realizedPnl.put(fill.symbol(), cumulativeRealizedPnl);
        cache.updatePosition(fill.symbol(), nextPosition);
        if (cache.hasInstrument(fill.symbol())) {
            String venue = cache.venue(fill.symbol());
                accountLedger.applyFill(venue, fill, realizedPnlDelta, cache.instrument(fill.symbol()));
                accountLedger.updatePosition(venue, cache.instrument(fill.symbol()), nextPosition,
                    averagePrices.getOrDefault(fill.symbol(), 0.0), fill.marketTimestamp());
        }
        return new PositionUpdate(
                fill.symbol(),
                fill.inputSequence(),
                fill.marketTimestamp(),
                fill.orderId(),
                fill.quantity(),
                nextPosition,
                realizedPnlDelta.doubleValue());
    }

    public BigDecimal position(String symbol) {
        return cache.position(symbol);
    }

    public double realizedPnl(String symbol) {
        return realizedPnl.getOrDefault(symbol, BigDecimal.ZERO).doubleValue();
    }

    public BigDecimal realizedPnlDecimal(String symbol) {
        return realizedPnl.getOrDefault(symbol, BigDecimal.ZERO);
    }

    public void configureAccount(String venue, double startingBalance, String currency, double leverage) {
        configureAccount(venue, startingBalance, currency, leverage, AccountType.MARGIN);
    }

    public void configureAccount(String venue, double startingBalance, String currency, double leverage,
            AccountType accountType) {
        accountLedger.configure(venue, startingBalance, currency, leverage, accountType);
    }

    public void configureAccount(String venue, BigDecimal startingBalance, String currency,
            BigDecimal leverage, AccountType accountType) {
        accountLedger.configure(venue, startingBalance, currency, leverage, accountType);
    }

    public void deposit(String venue, String currency, double amount) {
        accountLedger.deposit(venue, currency, amount);
    }

    public void deposit(String venue, String currency, BigDecimal amount) {
        accountLedger.deposit(venue, currency, amount);
    }

    public void setFxRate(String fromCurrency, String toCurrency, double rate) {
        accountLedger.setFxRate(fromCurrency, toCurrency, rate);
    }

    public void setFxRate(String fromCurrency, String toCurrency, BigDecimal rate) {
        accountLedger.setFxRate(fromCurrency, toCurrency, rate);
    }

    public void applyFxRate(FxRateUpdate update) {
        accountLedger.applyFxRate(update);
    }

    public AccountState applyMarketData(MarketDataSnapshot snapshot) {
        if (!cache.hasInstrument(snapshot.symbol())) return null;
        String venue = cache.venue(snapshot.symbol());
        accountLedger.updateMarketPrice(venue, cache.instrument(snapshot.symbol()), snapshot.mark(), snapshot.tsInit());
        return accountLedger.state(venue, snapshot.tsInit());
    }

    public Map<String, AccountState> accountStates(long timestamp) {
        return accountLedger.states(timestamp);
    }

    public boolean canReserve(OrderIntent order) {
        return accountLedger.canReserve(cache.venue(order.symbol()), order.quantity(), order.price(),
            cache.instrument(order.symbol()), order.side(), cache.position(order.symbol()));
    }

    public boolean canReserve(LimitOrderIntent order) {
        return accountLedger.canReserve(cache.venue(order.symbol()), order.quantity(), order.limitPrice(),
            cache.instrument(order.symbol()), order.side(), cache.position(order.symbol()));
    }

    public void releaseOrder(String symbol, String orderId) {
        if (cache.hasInstrument(symbol)) accountLedger.release(orderId);
    }

    public AccountState accountState(String venue, long timestamp) {
        return accountLedger.state(venue, timestamp);
    }
}