package com.abc.trading.execution;

import com.abc.trading.data.Price;
import com.abc.trading.data.Quantity;

/** Immutable canonical order lifecycle transition for replay and cross-runtime reconciliation. */
public record OrderEvent(
        String orderId,
        String exchangeOrderId,
        OrderEventType type,
        OrderStatus previousStatus,
        OrderStatus status,
        Quantity submittedQuantity,
        Quantity filledQuantity,
        Quantity remainingQuantity,
        Price averageFillPrice) {
    public OrderEvent {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (exchangeOrderId == null) throw new IllegalArgumentException("exchangeOrderId is required");
        if (type == null || status == null) throw new IllegalArgumentException("event type and status are required");
        if (submittedQuantity == null || filledQuantity == null || remainingQuantity == null) {
            throw new IllegalArgumentException("event quantities are required");
        }
        if (averageFillPrice == null) throw new IllegalArgumentException("averageFillPrice is required");
    }
}
