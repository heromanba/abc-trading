package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public record OrderExpired(
        String strategyId,
        String symbol,
        String orderId,
        SignalDirection side,
        Quantity remainingQuantity,
        double price,
                long marketTimestamp) {
        public OrderExpired(String strategyId, String symbol, String orderId, SignalDirection side,
                        int remainingQuantity, double price, long marketTimestamp) {
                this(strategyId, symbol, orderId, side, Quantity.fromInt(remainingQuantity), price, marketTimestamp);
        }
}