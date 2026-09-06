package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

import java.math.BigDecimal;

/** Serializable progress snapshot for restarting a parent execution algorithm. */
public record ExecutionAlgorithmState(
        String algorithmId,
        String symbol,
        SignalDirection side,
        Quantity targetQuantity,
        Quantity submittedQuantity,
        Quantity filledQuantity,
        long nextChildSequence,
        long scheduleIndex,
        BigDecimal observedVolume,
        long lastTimestampNs,
        boolean active) {
    public ExecutionAlgorithmState {
        if (algorithmId == null || algorithmId.isBlank()) throw new IllegalArgumentException("algorithmId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (targetQuantity == null || targetQuantity.isZero()) throw new IllegalArgumentException("targetQuantity is required");
        if (submittedQuantity == null || filledQuantity == null || observedVolume == null) {
            throw new IllegalArgumentException("algorithm quantities are required");
        }
        if (submittedQuantity.compareTo(targetQuantity) > 0 || filledQuantity.compareTo(submittedQuantity) > 0) {
            throw new IllegalArgumentException("algorithm quantities exceed target");
        }
        if (nextChildSequence < 0 || scheduleIndex < 0 || observedVolume.signum() < 0) {
            throw new IllegalArgumentException("algorithm progress is invalid");
        }
    }
}
