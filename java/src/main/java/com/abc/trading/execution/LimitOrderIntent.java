package com.abc.trading.execution;

import com.abc.trading.data.Quantity;
import java.math.BigDecimal;

public record LimitOrderIntent(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        Quantity quantity,
        double limitPrice,
        BigDecimal currentPosition,
        double realizedPnl,
        TimeInForce timeInForce,
        long expireTimeNs,
        double triggerPrice,
        TriggerType triggerType,
        double activationPrice,
        double trailingOffset,
        TrailingOffsetType trailingOffsetType,
        double limitOffset
) {
        public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, Quantity quantity, double limitPrice,
                        BigDecimal currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, limitPrice, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                                triggerPrice, triggerType, 0.0, 0.0, null, 0.0);
        }

        public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
                        BigDecimal currentPosition, double realizedPnl) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), limitPrice, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null, 0.0);
        }
        public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, Quantity quantity, double limitPrice,
                        BigDecimal currentPosition, double realizedPnl) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, limitPrice, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null, 0.0);
        }

        public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType, double activationPrice,
                        double trailingOffset, TrailingOffsetType trailingOffsetType, double limitOffset) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), limitPrice, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce,
                                expireTimeNs, triggerPrice, triggerType, activationPrice, trailingOffset,
                                trailingOffsetType, limitOffset);
        }

    public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), limitPrice, BigDecimal.valueOf(currentPosition), realizedPnl, TimeInForce.GTC, 0L,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null, 0.0);
    }

    public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), limitPrice, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce, expireTimeNs,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null, 0.0);
    }

    public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
            double triggerPrice, TriggerType triggerType) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), limitPrice, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce, expireTimeNs,
                triggerPrice, triggerType, 0.0, 0.0, null, 0.0);
    }
}
