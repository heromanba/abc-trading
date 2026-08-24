package com.abc.trading.portfolio;

import com.abc.trading.cache.Cache;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.data.MarketDataSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal deterministic portfolio state owner. */
public final class Portfolio {
    private final Cache cache;
    private final Map<String, Double> averagePrices = new LinkedHashMap<>();
    private final Map<String, Double> realizedPnl = new LinkedHashMap<>();
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
        int previousPosition = cache.position(fill.symbol());
        double previousAverage = averagePrices.getOrDefault(fill.symbol(), 0.0);
        int signedQuantity = fill.side() == com.abc.trading.execution.SignalDirection.BUY
                ? fill.quantity()
                : -fill.quantity();
        int nextPosition = previousPosition + signedQuantity;
        double realizedPnlDelta = -fill.commission().amount();

        if (previousPosition != 0 && Integer.signum(previousPosition) != Integer.signum(signedQuantity)) {
            int closedQuantity = Math.min(Math.abs(previousPosition), Math.abs(signedQuantity));
            double direction = previousPosition > 0 ? 1.0 : -1.0;
            realizedPnlDelta += (fill.price() - previousAverage) * closedQuantity * direction;
        }

        if (nextPosition == 0) {
            averagePrices.put(fill.symbol(), 0.0);
        } else if (previousPosition == 0
                || Integer.signum(previousPosition) == Integer.signum(signedQuantity)) {
            double total = Math.abs(previousPosition) * previousAverage
                    + Math.abs(signedQuantity) * fill.price();
            averagePrices.put(fill.symbol(), total / Math.abs(nextPosition));
        }

        double cumulativeRealizedPnl = realizedPnl.getOrDefault(fill.symbol(), 0.0) + realizedPnlDelta;
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
                nextPosition,
                nextPosition,
                realizedPnlDelta);
    }

    public int position(String symbol) {
        return cache.position(symbol);
    }

    public double realizedPnl(String symbol) {
        return realizedPnl.getOrDefault(symbol, 0.0);
    }

    public void configureAccount(String venue, double startingBalance, String currency, double leverage) {
        configureAccount(venue, startingBalance, currency, leverage, AccountType.MARGIN);
    }

    public void configureAccount(String venue, double startingBalance, String currency, double leverage,
            AccountType accountType) {
        accountLedger.configure(venue, startingBalance, currency, leverage, accountType);
    }

    public void deposit(String venue, String currency, double amount) {
        accountLedger.deposit(venue, currency, amount);
    }

    public void setFxRate(String fromCurrency, String toCurrency, double rate) {
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