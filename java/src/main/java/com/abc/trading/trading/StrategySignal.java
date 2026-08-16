package com.abc.trading.trading;

import com.abc.trading.execution.SignalDirection;

public record StrategySignal(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        SignalDirection side,
        double price,
        int currentPosition) {
}
