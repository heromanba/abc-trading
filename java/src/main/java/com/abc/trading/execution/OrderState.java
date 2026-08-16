package com.abc.trading.execution;

public record OrderState(
        String orderId,
        OrderStatus status,
        int submittedQuantity,
        int filledQuantity,
        double averageFillPrice) {
}
