package com.abc.trading.common.factories;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.model.orders.TrailingStopMarketOrder;
import com.abc.trading.model.orders.TrailingStopLimitOrder;
import com.abc.trading.model.orders.LimitOrder;
import com.abc.trading.model.orders.MarketOrder;
import com.abc.trading.model.orders.StopLimitOrder;
import com.abc.trading.model.orders.StopMarketOrder;
import com.abc.trading.data.Quantity;

public final class OrderFactory {
    private final String traderId;
    private final String strategyId;
    private long clientOrderSequence;

    public OrderFactory(String traderId, String strategyId) {
        if (traderId == null || traderId.isBlank()) throw new IllegalArgumentException("traderId is required");
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        this.traderId = traderId;
        this.strategyId = strategyId;
    }

    public String generateClientOrderId() {
        clientOrderSequence++;
        return strategyId + "-" + String.format("%06d", clientOrderSequence);
    }

    public LimitOrder limit(String symbol, SignalDirection side, int quantity, double price, long timestampNs) {
        return limit(symbol, side, Quantity.fromInt(quantity), price, timestampNs);
    }

    public LimitOrder limit(String symbol, SignalDirection side, Quantity quantity, double price, long timestampNs) {
        return new LimitOrder(generateClientOrderId(), strategyId, symbol, side, quantity, price, timestampNs);
    }

    public MarketOrder market(String symbol, SignalDirection side, int quantity, double price, long timestampNs) {
        return market(symbol, side, Quantity.fromInt(quantity), price, timestampNs);
    }

    public MarketOrder market(String symbol, SignalDirection side, Quantity quantity, double price, long timestampNs) {
        return new MarketOrder(generateClientOrderId(), strategyId, symbol, side, quantity, price, timestampNs);
    }

    public StopMarketOrder stopMarket(String symbol, SignalDirection side, int quantity,
            double triggerPrice, long timestampNs) {
        return stopMarket(symbol, side, quantity, triggerPrice, TriggerType.LAST_PRICE, timestampNs);
        }

        public StopMarketOrder stopMarket(String symbol, SignalDirection side, int quantity,
            double triggerPrice, TriggerType triggerType, long timestampNs) {
            return stopMarket(symbol, side, Quantity.fromInt(quantity), triggerPrice, triggerType, timestampNs);
        }

        public StopMarketOrder stopMarket(String symbol, SignalDirection side, Quantity quantity,
                double triggerPrice, TriggerType triggerType, long timestampNs) {
            return new StopMarketOrder(generateClientOrderId(), strategyId, symbol, side, quantity,
            triggerPrice, triggerType, timestampNs);
    }

    public StopLimitOrder stopLimit(String symbol, SignalDirection side, int quantity,
            double limitPrice, double triggerPrice, long timestampNs) {
        return stopLimit(symbol, side, quantity, limitPrice, triggerPrice, TriggerType.LAST_PRICE, timestampNs);
        }

        public StopLimitOrder stopLimit(String symbol, SignalDirection side, int quantity,
            double limitPrice, double triggerPrice, TriggerType triggerType, long timestampNs) {
            return stopLimit(symbol, side, Quantity.fromInt(quantity), limitPrice, triggerPrice, triggerType, timestampNs);
        }

        public StopLimitOrder stopLimit(String symbol, SignalDirection side, Quantity quantity,
                double limitPrice, double triggerPrice, TriggerType triggerType, long timestampNs) {
            return new StopLimitOrder(generateClientOrderId(), strategyId, symbol, side, quantity,
            limitPrice, triggerPrice, triggerType, timestampNs);
    }

        public TrailingStopMarketOrder trailingStopMarket(String symbol, SignalDirection side, int quantity,
            double activationPrice, double triggerPrice, TriggerType triggerType, double trailingOffset,
            TrailingOffsetType offsetType, long timestampNs) {
        return trailingStopMarket(symbol, side, Quantity.fromInt(quantity), activationPrice, triggerPrice,
            triggerType, trailingOffset, offsetType, timestampNs);
        }

        public TrailingStopMarketOrder trailingStopMarket(String symbol, SignalDirection side, Quantity quantity,
            double activationPrice, double triggerPrice, TriggerType triggerType, double trailingOffset,
            TrailingOffsetType offsetType, long timestampNs) {
        return new TrailingStopMarketOrder(generateClientOrderId(), strategyId, symbol, side, quantity,
            activationPrice, triggerPrice, triggerType, trailingOffset, offsetType, timestampNs);
        }

        public TrailingStopLimitOrder trailingStopLimit(String symbol, SignalDirection side, int quantity,
            double limitPrice, double activationPrice, double triggerPrice, TriggerType triggerType,
            double limitOffset, double trailingOffset, TrailingOffsetType offsetType, long timestampNs) {
        return trailingStopLimit(symbol, side, Quantity.fromInt(quantity), limitPrice, activationPrice, triggerPrice,
            triggerType, limitOffset, trailingOffset, offsetType, timestampNs);
        }

        public TrailingStopLimitOrder trailingStopLimit(String symbol, SignalDirection side, Quantity quantity,
            double limitPrice, double activationPrice, double triggerPrice, TriggerType triggerType,
            double limitOffset, double trailingOffset, TrailingOffsetType offsetType, long timestampNs) {
        return new TrailingStopLimitOrder(generateClientOrderId(), strategyId, symbol, side, quantity,
            limitPrice, activationPrice, triggerPrice, triggerType, limitOffset, trailingOffset,
            offsetType, timestampNs);
        }

    public String strategyId() {
        return strategyId;
    }

    public String traderId() {
        return traderId;
    }
}
