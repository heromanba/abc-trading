package com.abc.trading.common.factories;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.model.orders.LimitOrder;
import com.abc.trading.model.orders.MarketOrder;
import com.abc.trading.model.orders.StopLimitOrder;
import com.abc.trading.model.orders.StopMarketOrder;

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
        return new LimitOrder(generateClientOrderId(), strategyId, symbol, side, quantity, price, timestampNs);
    }

    public MarketOrder market(String symbol, SignalDirection side, int quantity, double price, long timestampNs) {
        return new MarketOrder(generateClientOrderId(), strategyId, symbol, side, quantity, price, timestampNs);
    }

    public StopMarketOrder stopMarket(String symbol, SignalDirection side, int quantity,
            double triggerPrice, long timestampNs) {
        return stopMarket(symbol, side, quantity, triggerPrice, TriggerType.LAST_PRICE, timestampNs);
        }

        public StopMarketOrder stopMarket(String symbol, SignalDirection side, int quantity,
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
        return new StopLimitOrder(generateClientOrderId(), strategyId, symbol, side, quantity,
            limitPrice, triggerPrice, triggerType, timestampNs);
    }

    public String strategyId() {
        return strategyId;
    }

    public String traderId() {
        return traderId;
    }
}
