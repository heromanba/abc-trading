package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.data.Quantity;

public record TrailingStopMarketOrder(
        String clientOrderId, String strategyId, String symbol, SignalDirection side, Quantity quantity,
        double activationPrice, double triggerPrice, TriggerType triggerType, double trailingOffset,
        TrailingOffsetType trailingOffsetType, long timestampNs) implements Order {
    public TrailingStopMarketOrder {
        validate(clientOrderId, strategyId, symbol, side, quantity, triggerType, trailingOffset, trailingOffsetType);
        if (activationPrice < 0.0 || !Double.isFinite(activationPrice)) throw new IllegalArgumentException("activationPrice must be non-negative");
        if (triggerPrice < 0.0 || !Double.isFinite(triggerPrice)) throw new IllegalArgumentException("triggerPrice must be non-negative");
    }

    @Override public double price() { return triggerPrice; }
    @Override public double triggerPrice() { return triggerPrice; }
    @Override public TriggerType triggerType() { return triggerType; }
    @Override public double activationPrice() { return activationPrice; }
    @Override public double trailingOffset() { return trailingOffset; }
    @Override public TrailingOffsetType trailingOffsetType() { return trailingOffsetType; }

        public TrailingStopMarketOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            int quantity, double activationPrice, double triggerPrice, TriggerType triggerType, double trailingOffset,
            TrailingOffsetType trailingOffsetType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, Quantity.fromInt(quantity), activationPrice, triggerPrice,
            triggerType, trailingOffset, trailingOffsetType, timestampNs);
        }

        static void validate(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            Quantity quantity, TriggerType triggerType, double trailingOffset, TrailingOffsetType offsetType) {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (triggerType == null || triggerType == TriggerType.NO_TRIGGER) throw new IllegalArgumentException("triggerType is required");
        if (!Double.isFinite(trailingOffset) || trailingOffset <= 0.0) throw new IllegalArgumentException("trailingOffset must be positive");
            if (offsetType == null || offsetType == TrailingOffsetType.PRICE_TIER) {
                throw new IllegalArgumentException("unsupported trailing offset type");
            }
    }
}