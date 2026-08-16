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
        double realizedPnl
) {
}
