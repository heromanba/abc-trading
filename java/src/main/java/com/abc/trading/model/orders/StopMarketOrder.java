package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

public record StopMarketOrder(
        String clientOrderId,
        String strategyId,
        String symbol,
        SignalDirection side,
        Quantity quantity,
        Price triggerPrice,
        TriggerType triggerType,
        long timestampNs
) implements Order {
    public StopMarketOrder {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity == null || quantity.isZero()) throw new IllegalArgumentException("quantity must be positive");
        if (triggerPrice == null) throw new IllegalArgumentException("triggerPrice is required");
        if (triggerType == null || triggerType == TriggerType.NO_TRIGGER) throw new IllegalArgumentException("triggerType is required");
    }

    public StopMarketOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            int quantity, double triggerPrice, TriggerType triggerType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, Quantity.fromInt(quantity), Price.fromDouble(triggerPrice), triggerType, timestampNs);
    }

    public StopMarketOrder(String clientOrderId, String strategyId, String symbol, SignalDirection side,
            Quantity quantity, double triggerPrice, TriggerType triggerType, long timestampNs) {
        this(clientOrderId, strategyId, symbol, side, quantity, Price.fromDouble(triggerPrice), triggerType, timestampNs);
    }

    @Override
    public Price price() {
        return triggerPrice;
    }

    @Override
    public TriggerType triggerType() { return triggerType; }
}