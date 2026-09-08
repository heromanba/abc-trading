package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

public record StopLimitOrder(
        String clientOrderId,
        String strategyId,
        String symbol,
        SignalDirection side,
        Quantity quantity,
        Price price,
        Price triggerPrice,
        TriggerType triggerType,
        long timestampNs
) implements Order {
    public StopLimitOrder {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (price == null || triggerPrice == null) throw new IllegalArgumentException("prices are required");
        if (triggerType == null || triggerType == TriggerType.NO_TRIGGER) throw new IllegalArgumentException("triggerType is required");
    }

    public StopLimitOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            int quantity, double price, double triggerPrice, TriggerType triggerType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, Quantity.fromInt(quantity), Price.fromDouble(price), Price.fromDouble(triggerPrice), triggerType, timestampNs);
    }

    public StopLimitOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            Quantity quantity, double price, double triggerPrice, TriggerType triggerType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, quantity, Price.fromDouble(price), Price.fromDouble(triggerPrice), triggerType, timestampNs);
    }

    @Override
    public Price triggerPrice() { return triggerPrice; }

    @Override
    public TriggerType triggerType() { return triggerType; }
}