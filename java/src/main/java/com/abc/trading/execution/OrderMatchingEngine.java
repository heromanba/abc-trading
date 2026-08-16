package com.abc.trading.execution;

/** Deterministic matching rules for market and limit orders. */
public final class OrderMatchingEngine {
    public OrderFill matchMarketOrder(OrderIntent order, double marketPrice) {
        if (!Double.isFinite(marketPrice) || marketPrice <= 0.0) {
            throw new IllegalArgumentException("marketPrice must be finite and positive");
        }
        return new OrderFill(
                order.strategyId(), order.symbol(), order.inputSequence(), order.marketTimestamp(),
                order.correlationId(), order.orderId(), order.side(), order.quantity(), marketPrice,
                order.currentPosition(), order.realizedPnl());
    }

    public OrderFill matchLimitOrder(OrderIntent order, double marketPrice) {
        throw new UnsupportedOperationException("Use LimitOrderIntent for limit orders");
    }

    public OrderFill matchLimitOrder(LimitOrderIntent order, double marketPrice) {
        if (!Double.isFinite(marketPrice) || marketPrice <= 0.0) {
            throw new IllegalArgumentException("marketPrice must be finite and positive");
        }
        boolean crossed = order.side() == SignalDirection.BUY
                ? marketPrice <= order.limitPrice()
                : marketPrice >= order.limitPrice();
        if (!crossed) return null;
        return new OrderFill(
                order.strategyId(), order.symbol(), order.inputSequence(), order.marketTimestamp(),
                order.correlationId(), order.orderId(), order.side(), order.quantity(), order.limitPrice(),
                order.currentPosition(), order.realizedPnl());
    }
}
