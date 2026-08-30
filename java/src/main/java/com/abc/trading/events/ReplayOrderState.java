package com.abc.trading.events;

import com.abc.trading.execution.OrderStatus;
import com.abc.trading.data.Quantity;

/** Reconstructed order state derived from canonical persisted events. */
public record ReplayOrderState(
        String orderId,
        OrderStatus status,
        Quantity submittedQuantity,
        Quantity filledQuantity,
        Quantity remainingQuantity,
        double averageFillPrice,
        long lastLifecycleSequence) {
    public ReplayOrderState {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (submittedQuantity == null || filledQuantity == null || remainingQuantity == null) {
            throw new IllegalArgumentException("quantities must be non-negative");
        }
    }

    public ReplayOrderState(String orderId, OrderStatus status, int submittedQuantity,
            int filledQuantity, int remainingQuantity, double averageFillPrice,
            long lastLifecycleSequence) {
        this(orderId, status, Quantity.fromInt(submittedQuantity), Quantity.fromInt(filledQuantity),
                Quantity.fromInt(remainingQuantity), averageFillPrice, lastLifecycleSequence);
    }
}
