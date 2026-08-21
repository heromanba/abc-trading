package com.abc.trading.execution;

public record LimitOrderIntent(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        int quantity,
        double limitPrice,
        int currentPosition,
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
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, limitPrice, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null, 0.0);
    }

    public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, limitPrice, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null, 0.0);
    }

    public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
            double triggerPrice, TriggerType triggerType) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, limitPrice, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                triggerPrice, triggerType, 0.0, 0.0, null, 0.0);
    }
}
