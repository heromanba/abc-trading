package com.abc.trading.execution;

public record OrderIntent(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        int quantity,
        double price,
        int currentPosition,
        double realizedPnl,
        TimeInForce timeInForce,
        long expireTimeNs
) {
    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, price, currentPosition, realizedPnl, TimeInForce.GTC, 0L);
    }
}
