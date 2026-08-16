package com.abc.trading.portfolio;

import com.abc.trading.cache.Cache;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.OrderFill;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal deterministic portfolio state owner. */
public final class Portfolio {
    private final Cache cache;
    private final Map<String, Double> averagePrices = new LinkedHashMap<>();
    private final Map<String, Double> realizedPnl = new LinkedHashMap<>();

    public Portfolio(Cache cache) {
        this.cache = cache;
    }

    public void applyOrderIntent(OrderIntent order) {
        cache.recordOrder(order);
    }

    public PositionUpdate applyFill(OrderFill fill) {
        int previousPosition = cache.position(fill.symbol());
        double previousAverage = averagePrices.getOrDefault(fill.symbol(), 0.0);
        int signedQuantity = fill.side() == com.abc.trading.execution.SignalDirection.BUY
                ? fill.quantity()
                : -fill.quantity();
        int nextPosition = previousPosition + signedQuantity;
        double pnl = realizedPnl.getOrDefault(fill.symbol(), 0.0) - fill.commission().amount();

        if (previousPosition != 0 && Integer.signum(previousPosition) != Integer.signum(signedQuantity)) {
            int closedQuantity = Math.min(Math.abs(previousPosition), Math.abs(signedQuantity));
            double direction = previousPosition > 0 ? 1.0 : -1.0;
            pnl += (fill.price() - previousAverage) * closedQuantity * direction;
        }

        if (nextPosition == 0) {
            averagePrices.put(fill.symbol(), 0.0);
        } else if (previousPosition == 0
                || Integer.signum(previousPosition) == Integer.signum(signedQuantity)) {
            double total = Math.abs(previousPosition) * previousAverage
                    + Math.abs(signedQuantity) * fill.price();
            averagePrices.put(fill.symbol(), total / Math.abs(nextPosition));
        }

        realizedPnl.put(fill.symbol(), pnl);
        cache.updatePosition(fill.symbol(), nextPosition);
        return new PositionUpdate(
                fill.symbol(),
                fill.inputSequence(),
                fill.marketTimestamp(),
                fill.orderId(),
                fill.quantity(),
                nextPosition,
                pnl);
    }

    public int position(String symbol) {
        return cache.position(symbol);
    }

    public double realizedPnl(String symbol) {
        return realizedPnl.getOrDefault(symbol, 0.0);
    }
}