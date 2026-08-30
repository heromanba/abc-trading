package com.abc.trading.trading;

import com.abc.trading.execution.SignalDirection;

import java.math.BigDecimal;

public record StrategySignal(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        SignalDirection side,
        double price,
        BigDecimal currentPosition) {
    public StrategySignal(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, SignalDirection side, double price, int currentPosition) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, side, price,
                BigDecimal.valueOf(currentPosition));
    }
}
