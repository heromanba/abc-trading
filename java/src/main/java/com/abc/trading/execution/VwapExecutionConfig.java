package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

import java.math.BigDecimal;

/** Trade-volume participation parameters for a VWAP/POV parent order. */
public record VwapExecutionConfig(
        String algorithmId,
        String symbol,
        SignalDirection side,
        Quantity targetQuantity,
        long startTimestampNs,
        long endTimestampNs,
        BigDecimal participationRate,
        Quantity minimumSlice,
        Quantity maximumSlice,
        TimeInForce timeInForce) {
    public VwapExecutionConfig {
        if (algorithmId == null || algorithmId.isBlank()) throw new IllegalArgumentException("algorithmId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (targetQuantity == null || targetQuantity.isZero()) throw new IllegalArgumentException("targetQuantity is required");
        if (endTimestampNs <= startTimestampNs) throw new IllegalArgumentException("endTimestampNs must exceed startTimestampNs");
        if (participationRate == null || participationRate.signum() <= 0
                || participationRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("participationRate must be in (0, 1]");
        }
        if (minimumSlice == null || minimumSlice.isZero()) throw new IllegalArgumentException("minimumSlice is required");
        if (maximumSlice == null || maximumSlice.compareTo(minimumSlice) < 0) {
            throw new IllegalArgumentException("maximumSlice must cover minimumSlice");
        }
        if (timeInForce == null) throw new IllegalArgumentException("timeInForce is required");
    }
}
