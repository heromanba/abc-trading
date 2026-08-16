package com.abc.trading.execution;

/** Matching-engine boundary; market matching is implemented, other order types are pending. */
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
        throw new UnsupportedOperationException("Limit-order matching is not implemented");
    }
}
