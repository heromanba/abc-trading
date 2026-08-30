package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public record OrderIntent(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        Quantity quantity,
        double price,
        int currentPosition,
        double realizedPnl,
        TimeInForce timeInForce,
        long expireTimeNs,
        double triggerPrice,
        TriggerType triggerType,
        double activationPrice,
        double trailingOffset,
        TrailingOffsetType trailingOffsetType
) {
        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType, double activationPrice,
                        double trailingOffset, TrailingOffsetType trailingOffsetType) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), price, currentPosition, realizedPnl, timeInForce,
                                expireTimeNs, triggerPrice, triggerType, activationPrice, trailingOffset, trailingOffsetType);
        }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, Quantity quantity, double price,
                        int currentPosition, double realizedPnl) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, price, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
        }

    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), price, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
    }

    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
    }

    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
            double triggerPrice) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                triggerPrice, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
    }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                                triggerPrice, triggerType, 0.0, 0.0, null);
        }
}
