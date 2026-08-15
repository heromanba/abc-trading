package com.abc.trading.risk;

import com.abc.trading.execution.OrderIntent;

/** Minimal synchronous risk boundary for trading commands. */
public final class RiskEngine {
    private final int maxQuantity;

    public RiskEngine(int maxQuantity) {
        if (maxQuantity <= 0) throw new IllegalArgumentException("maxQuantity must be positive");
        this.maxQuantity = maxQuantity;
    }

    public RiskDecision evaluate(OrderIntent order) {
        if (order.quantity() > maxQuantity) {
            return RiskDecision.rejected("quantity exceeds maxQuantity");
        }
        return RiskDecision.allow();
    }
}