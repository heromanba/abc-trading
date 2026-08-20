package com.abc.trading.execution;

public record OrderState(
        String orderId,
        OrderStatus status,
        int submittedQuantity,
        int filledQuantity,
                int remainingQuantity,
                double averageFillPrice,
                TimeInForce timeInForce,
                long expireTimeNs) {
        public OrderState {
                if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
                if (status == null) throw new IllegalArgumentException("status is required");
                if (submittedQuantity <= 0) throw new IllegalArgumentException("submittedQuantity must be positive");
                if (filledQuantity < 0 || remainingQuantity < 0) {
                        throw new IllegalArgumentException("fill quantities must be non-negative");
                }
                if (filledQuantity + remainingQuantity != submittedQuantity) {
                        throw new IllegalArgumentException("fill quantities must equal submitted quantity");
                }
                if (timeInForce == null) throw new IllegalArgumentException("timeInForce is required");
        }

        public OrderState(String orderId, OrderStatus status, int submittedQuantity,
                        int filledQuantity, double averageFillPrice) {
                this(orderId, status, submittedQuantity, filledQuantity,
                                submittedQuantity - filledQuantity, averageFillPrice, TimeInForce.GTC, 0L);
        }
}
