package com.abc.trading.events;

import com.abc.trading.execution.OrderStatus;

/** Reconstructed order state derived from canonical persisted events. */
public record ReplayOrderState(
        String orderId,
        OrderStatus status,
        int submittedQuantity,
        int filledQuantity,
        int remainingQuantity,
        double averageFillPrice,
        long lastLifecycleSequence) {
    public ReplayOrderState {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (submittedQuantity < 0 || filledQuantity < 0 || remainingQuantity < 0) {
            throw new IllegalArgumentException("quantities must be non-negative");
        }
    }
}
