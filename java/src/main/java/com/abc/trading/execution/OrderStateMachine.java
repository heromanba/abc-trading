package com.abc.trading.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderStateMachine {
    private final Map<String, OrderState> states = new LinkedHashMap<>();

    public OrderState initialize(String orderId, int quantity, TimeInForce timeInForce, long expireTimeNs) {
        if (states.containsKey(orderId)) throw new IllegalStateException("Duplicate order: " + orderId);
        OrderState state = new OrderState(orderId, OrderStatus.INITIALIZED, quantity, 0, quantity,
                0.0, timeInForce, expireTimeNs);
        states.put(orderId, state);
        return state;
    }

    public OrderState submit(String orderId) {
        return transition(orderId, OrderStatus.SUBMITTED, OrderStatus.INITIALIZED, OrderStatus.RELEASED);
    }

    public OrderState emulate(String orderId) {
        return transition(orderId, OrderStatus.EMULATED, OrderStatus.INITIALIZED);
    }

    public OrderState release(String orderId) {
        return transition(orderId, OrderStatus.RELEASED, OrderStatus.EMULATED);
    }

    public OrderState accept(String orderId) {
        return transition(orderId, OrderStatus.ACCEPTED, OrderStatus.SUBMITTED, OrderStatus.TRIGGERED);
    }

    public OrderState trigger(String orderId) {
        return transition(orderId, OrderStatus.TRIGGERED, OrderStatus.ACCEPTED);
    }

    public OrderState deny(String orderId) {
        return transition(orderId, OrderStatus.DENIED, OrderStatus.INITIALIZED);
    }

    public OrderState reject(String orderId) {
        return transition(orderId, OrderStatus.REJECTED, OrderStatus.SUBMITTED, OrderStatus.ACCEPTED);
    }

    public OrderState pendingCancel(String orderId) {
        return transition(orderId, OrderStatus.PENDING_CANCEL, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED);
    }

    public OrderState cancel(String orderId) {
        return transition(orderId, OrderStatus.CANCELED, OrderStatus.PENDING_CANCEL,
                OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED);
    }

    public OrderState cancelReject(String orderId) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_CANCEL) {
            throw new IllegalStateException("Cannot reject cancel from " + current.status());
        }
        return restoreOpenState(current);
    }

    public OrderState pendingUpdate(String orderId) {
        return transition(orderId, OrderStatus.PENDING_UPDATE, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED);
    }

    public OrderState update(String orderId, int quantity) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_UPDATE) {
            throw new IllegalStateException("Cannot update order from " + current.status());
        }
        if (quantity < current.filledQuantity() || quantity <= 0) {
            throw new IllegalArgumentException("updated quantity must cover existing fills");
        }
        OrderStatus updatedStatus = current.filledQuantity() == 0
            ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
        OrderState updated = new OrderState(orderId, updatedStatus, quantity,
                current.filledQuantity(), quantity - current.filledQuantity(), current.averageFillPrice(),
                current.timeInForce(), current.expireTimeNs());
        states.put(orderId, updated);
        return updated;
    }

    public OrderState updateReject(String orderId) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_UPDATE) {
            throw new IllegalStateException("Cannot reject update from " + current.status());
        }
        return restoreOpenState(current);
    }

    public OrderState fill(String orderId, int quantity, double price) {
        OrderState current = state(orderId);
        if (quantity <= 0 || quantity > current.remainingQuantity()) {
            throw new IllegalArgumentException("fill quantity exceeds remaining quantity");
        }
        if (!current.status().isOpen() && current.status() != OrderStatus.SUBMITTED) {
            throw new IllegalStateException("Cannot fill order from " + current.status());
        }
        int filledQuantity = current.filledQuantity() + quantity;
        int remainingQuantity = current.submittedQuantity() - filledQuantity;
        double averagePrice = (current.averageFillPrice() * current.filledQuantity() + price * quantity)
                / filledQuantity;
        OrderStatus status = remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        OrderState updated = new OrderState(orderId, status, current.submittedQuantity(), filledQuantity,
                remainingQuantity, averagePrice, current.timeInForce(), current.expireTimeNs());
        states.put(orderId, updated);
        return updated;
    }

    public OrderState expire(String orderId) {
        return transition(orderId, OrderStatus.EXPIRED, OrderStatus.ACCEPTED,
                OrderStatus.PARTIALLY_FILLED, OrderStatus.PENDING_CANCEL);
    }

    public OrderState voidOrder(String orderId) {
        return transition(orderId, OrderStatus.VOIDED,
                OrderStatus.INITIALIZED, OrderStatus.EMULATED, OrderStatus.RELEASED,
                OrderStatus.SUBMITTED, OrderStatus.ACCEPTED, OrderStatus.TRIGGERED,
                OrderStatus.PENDING_UPDATE, OrderStatus.PENDING_CANCEL,
                OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED,
                OrderStatus.CANCELED, OrderStatus.EXPIRED, OrderStatus.REJECTED);
    }

    public OrderState state(String orderId) {
        OrderState state = states.get(orderId);
        if (state == null) throw new IllegalArgumentException("Unknown order: " + orderId);
        return state;
    }

    public Map<String, OrderState> states() {
        return Map.copyOf(states);
    }

    private OrderState transition(String orderId, OrderStatus target, OrderStatus... allowed) {
        OrderState current = state(orderId);
        for (OrderStatus status : allowed) {
            if (current.status() == status) {
                OrderState updated = new OrderState(orderId, target, current.submittedQuantity(),
                        current.filledQuantity(), current.remainingQuantity(), current.averageFillPrice(),
                        current.timeInForce(), current.expireTimeNs());
                states.put(orderId, updated);
                return updated;
            }
        }
        throw new IllegalStateException("Cannot transition order from " + current.status() + " to " + target);
    }

    private OrderState restoreOpenState(OrderState current) {
        OrderStatus restoredStatus = current.filledQuantity() == 0
                ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
        OrderState restored = new OrderState(current.orderId(), restoredStatus, current.submittedQuantity(),
                current.filledQuantity(), current.remainingQuantity(), current.averageFillPrice(),
                current.timeInForce(), current.expireTimeNs());
        states.put(current.orderId(), restored);
        return restored;
    }
}