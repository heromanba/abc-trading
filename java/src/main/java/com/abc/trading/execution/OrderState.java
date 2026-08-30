package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

public record OrderState(
        String orderId,
        OrderStatus status,
        Quantity submittedQuantity,
        Quantity filledQuantity,
                Quantity remainingQuantity,
                double averageFillPrice,
                TimeInForce timeInForce,
                long expireTimeNs) {
        public OrderState {
                if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
                if (status == null) throw new IllegalArgumentException("status is required");
                if (submittedQuantity == null || submittedQuantity.isZero()) throw new IllegalArgumentException("submittedQuantity must be positive");
                if (filledQuantity == null || remainingQuantity == null) {
                        throw new IllegalArgumentException("fill quantities must be non-negative");
                }
                if (filledQuantity.compareTo(Quantity.fromInt(0)) < 0 || remainingQuantity.compareTo(Quantity.fromInt(0)) < 0) {
                        throw new IllegalArgumentException("fill quantities must be non-negative");
                }
                if (!filledQuantity.add(remainingQuantity).equals(submittedQuantity)) {
                        throw new IllegalArgumentException("fill quantities must equal submitted quantity");
                }
                if (timeInForce == null) throw new IllegalArgumentException("timeInForce is required");
        }

        public OrderState(String orderId, OrderStatus status, int submittedQuantity,
                        int filledQuantity, double averageFillPrice) {
                this(orderId, status, Quantity.fromInt(submittedQuantity), Quantity.fromInt(filledQuantity),
                                Quantity.fromInt(submittedQuantity - filledQuantity), averageFillPrice, TimeInForce.GTC, 0L);
        }

        public OrderState(String orderId, OrderStatus status, int submittedQuantity,
                int filledQuantity, int remainingQuantity, double averageFillPrice,
                TimeInForce timeInForce, long expireTimeNs) {
                this(orderId, status, Quantity.fromInt(submittedQuantity), Quantity.fromInt(filledQuantity),
                        Quantity.fromInt(remainingQuantity), averageFillPrice, timeInForce, expireTimeNs);
        }
}
