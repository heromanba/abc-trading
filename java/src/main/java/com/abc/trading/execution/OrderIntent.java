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
        double realizedPnl
) {
}
