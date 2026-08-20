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
        long expireTimeNs
) {
    public LimitOrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double limitPrice,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, limitPrice, currentPosition, realizedPnl, TimeInForce.GTC, 0L);
    }
}
