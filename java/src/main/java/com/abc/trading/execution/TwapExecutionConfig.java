package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

/** Deterministic time-slicing parameters for a TWAP parent order. */
public record TwapExecutionConfig(
        String algorithmId,
        String symbol,
        SignalDirection side,
        Quantity targetQuantity,
        long startTimestampNs,
        long endTimestampNs,
        long sliceIntervalNs,
        TimeInForce timeInForce) {
    public TwapExecutionConfig {
        if (algorithmId == null || algorithmId.isBlank()) throw new IllegalArgumentException("algorithmId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (targetQuantity == null || targetQuantity.isZero()) throw new IllegalArgumentException("targetQuantity is required");
        if (endTimestampNs <= startTimestampNs) throw new IllegalArgumentException("endTimestampNs must exceed startTimestampNs");
        if (sliceIntervalNs <= 0) throw new IllegalArgumentException("sliceIntervalNs must be positive");
        if (timeInForce == null) throw new IllegalArgumentException("timeInForce is required");
    }

    public long totalSlices() {
        long duration = endTimestampNs - startTimestampNs;
        return Math.max(1, (duration + sliceIntervalNs - 1) / sliceIntervalNs);
    }
}
