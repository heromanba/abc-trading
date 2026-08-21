package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.TriggerType;

public record TrailingStopLimitOrder(
        String clientOrderId, String strategyId, String symbol, SignalDirection side, int quantity,
        double limitPrice, double activationPrice, double triggerPrice, TriggerType triggerType,
        double limitOffset, double trailingOffset, TrailingOffsetType trailingOffsetType, long timestampNs)
        implements Order {
    public TrailingStopLimitOrder {
        TrailingStopMarketOrder.validate(clientOrderId, strategyId, symbol, side, quantity,
                triggerType, trailingOffset, trailingOffsetType);
        if (!Double.isFinite(limitPrice) || limitPrice <= 0.0) throw new IllegalArgumentException("limitPrice must be positive");
        if (activationPrice < 0.0 || !Double.isFinite(activationPrice)) throw new IllegalArgumentException("activationPrice must be non-negative");
        if (triggerPrice < 0.0 || !Double.isFinite(triggerPrice)) throw new IllegalArgumentException("triggerPrice must be non-negative");
        if (!Double.isFinite(limitOffset)) throw new IllegalArgumentException("limitOffset must be finite");
    }

    @Override public double price() { return limitPrice; }
    @Override public double triggerPrice() { return triggerPrice; }
    @Override public TriggerType triggerType() { return triggerType; }
    @Override public double activationPrice() { return activationPrice; }
    @Override public double trailingOffset() { return trailingOffset; }
    @Override public TrailingOffsetType trailingOffsetType() { return trailingOffsetType; }
    @Override public double limitOffset() { return limitOffset; }
}