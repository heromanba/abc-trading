package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

public record TrailingStopLimitOrder(
        String clientOrderId, String strategyId, String symbol, SignalDirection side, Quantity quantity,
        Price limitPrice, Price activationPrice, Price triggerPrice, TriggerType triggerType,
        double limitOffset, double trailingOffset, TrailingOffsetType trailingOffsetType, long timestampNs)
        implements Order {
    public TrailingStopLimitOrder {
        TrailingStopMarketOrder.validate(clientOrderId, strategyId, symbol, side, quantity,
                triggerType, trailingOffset, trailingOffsetType);
        if (limitPrice == null || activationPrice == null || triggerPrice == null) throw new IllegalArgumentException("prices are required");
        if (!Double.isFinite(limitOffset)) throw new IllegalArgumentException("limitOffset must be finite");
    }

    public TrailingStopLimitOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            int quantity, double limitPrice, double activationPrice, double triggerPrice, TriggerType triggerType,
            double limitOffset, double trailingOffset, TrailingOffsetType trailingOffsetType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, Quantity.fromInt(quantity), Price.fromDouble(limitPrice), Price.fromDouble(activationPrice),
            Price.fromDouble(triggerPrice), triggerType, limitOffset, trailingOffset, trailingOffsetType, timestampNs);
    }

        public TrailingStopLimitOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            Quantity quantity, double limitPrice, double activationPrice, double triggerPrice, TriggerType triggerType,
            double limitOffset, double trailingOffset, TrailingOffsetType trailingOffsetType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, quantity, Price.fromDouble(limitPrice), Price.fromDouble(activationPrice),
            Price.fromDouble(triggerPrice), triggerType, limitOffset, trailingOffset, trailingOffsetType, timestampNs);
        }

    @Override public Price price() { return limitPrice; }
    @Override public Price triggerPrice() { return triggerPrice; }
    @Override public TriggerType triggerType() { return triggerType; }
    @Override public Price activationPrice() { return activationPrice; }
    @Override public double trailingOffset() { return trailingOffset; }
    @Override public TrailingOffsetType trailingOffsetType() { return trailingOffsetType; }
    @Override public double limitOffset() { return limitOffset; }
}