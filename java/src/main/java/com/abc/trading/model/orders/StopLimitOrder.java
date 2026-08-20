package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;

public record StopLimitOrder(
        String clientOrderId,
        String strategyId,
        String symbol,
        SignalDirection side,
        int quantity,
        double price,
        double triggerPrice,
        TriggerType triggerType,
        long timestampNs
) implements Order {
    public StopLimitOrder {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be positive");
        if (!Double.isFinite(triggerPrice) || triggerPrice <= 0.0) throw new IllegalArgumentException("triggerPrice must be positive");
        if (triggerType == null || triggerType == TriggerType.NO_TRIGGER) throw new IllegalArgumentException("triggerType is required");
    }

    @Override
    public double triggerPrice() { return triggerPrice; }

    @Override
    public TriggerType triggerType() { return triggerType; }
}