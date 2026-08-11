package com.abc.trading.events;

import com.abc.trading.execution.SignalDirection;

public record Event(
        long marketTimestamp,
        EventType eventType,
        String strategyId,
        SignalDirection signalDirection,
        String correlationId,
        String orderId,
        double price,
        int quantity,
        int currentPosition,
        double realizedPnl
) {
}
