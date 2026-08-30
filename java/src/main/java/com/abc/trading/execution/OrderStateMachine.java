package com.abc.trading.execution;

import com.abc.trading.data.Quantity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderStateMachine {
    private final Map<String, OrderState> states = new LinkedHashMap<>();
    private final Map<String, OrderStatus> pendingPreviousStatuses = new LinkedHashMap<>();

    public OrderState initialize(String orderId, int quantity, TimeInForce timeInForce, long expireTimeNs) {
        return initialize(orderId, Quantity.fromInt(quantity), timeInForce, expireTimeNs);
    }

    public OrderState initialize(String orderId, Quantity quantity, TimeInForce timeInForce, long expireTimeNs) {
        if (states.containsKey(orderId)) throw new IllegalStateException("Duplicate order: " + orderId);
        OrderState state = new OrderState(orderId, OrderStatus.INITIALIZED, quantity, Quantity.fromInt(0), quantity,
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
        return transition(orderId, OrderStatus.ACCEPTED, OrderStatus.SUBMITTED);
    }

    public OrderState trigger(String orderId) {
        return transition(orderId, OrderStatus.TRIGGERED, OrderStatus.ACCEPTED);
    }

    public OrderState deny(String orderId) {
        return transition(orderId, OrderStatus.DENIED, OrderStatus.INITIALIZED, OrderStatus.RELEASED);
    }

    public OrderState reject(String orderId) {
        return transition(orderId, OrderStatus.REJECTED, OrderStatus.SUBMITTED, OrderStatus.ACCEPTED,
            OrderStatus.TRIGGERED, OrderStatus.PENDING_UPDATE);
    }

    public OrderState pendingCancel(String orderId) {
        OrderState current = state(orderId);
        if (!isCancellable(current.status())) {
            throw new IllegalStateException("Cannot cancel order from " + current.status());
        }
        pendingPreviousStatuses.put(orderId, current.status());
        return transition(orderId, OrderStatus.PENDING_CANCEL, current.status());
    }

    public OrderState cancel(String orderId) {
        OrderState updated = transition(orderId, OrderStatus.CANCELED, OrderStatus.PENDING_CANCEL,
            OrderStatus.EMULATED, OrderStatus.RELEASED, OrderStatus.SUBMITTED,
            OrderStatus.ACCEPTED, OrderStatus.TRIGGERED, OrderStatus.PARTIALLY_FILLED);
        pendingPreviousStatuses.remove(orderId);
        return updated;
    }

    public OrderState cancelReject(String orderId) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_CANCEL) {
            throw new IllegalStateException("Cannot reject cancel from " + current.status());
        }
        return restoreOpenState(current);
    }

    public OrderState pendingUpdate(String orderId) {
        OrderState current = state(orderId);
        if (!isModifiable(current.status())) {
            throw new IllegalStateException("Cannot modify order from " + current.status());
        }
        pendingPreviousStatuses.put(orderId, current.status());
        return transition(orderId, OrderStatus.PENDING_UPDATE, current.status());
    }

    public OrderState update(String orderId, int quantity) {
        return update(orderId, Quantity.fromInt(quantity));
    }

    public OrderState update(String orderId, Quantity quantity) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_UPDATE) {
            throw new IllegalStateException("Cannot update order from " + current.status());
        }
        if (quantity.compareTo(current.filledQuantity()) < 0 || quantity.isZero()) {
            throw new IllegalArgumentException("updated quantity must cover existing fills");
        }
        OrderStatus previousStatus = pendingPreviousStatuses.remove(orderId);
        OrderStatus updatedStatus = previousStatus == OrderStatus.TRIGGERED
            ? OrderStatus.TRIGGERED
            : current.filledQuantity().isZero() ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
        OrderState updated = new OrderState(orderId, updatedStatus, quantity,
                current.filledQuantity(), quantity.subtract(current.filledQuantity()), current.averageFillPrice(),
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
        return fill(orderId, Quantity.fromInt(quantity), price);
    }

    public OrderState fill(String orderId, Quantity quantity, double price) {
        OrderState current = state(orderId);
        if (current.status() == OrderStatus.TRIGGERED && !quantity.equals(current.remainingQuantity())) {
            throw new IllegalArgumentException("triggered order must fill completely");
        }
        if (quantity.isZero() || quantity.compareTo(current.remainingQuantity()) > 0) {
            throw new IllegalArgumentException("fill quantity exceeds remaining quantity");
        }
        if (!current.status().isOpen() && current.status() != OrderStatus.SUBMITTED) {
            throw new IllegalStateException("Cannot fill order from " + current.status());
        }
        Quantity filledQuantity = current.filledQuantity().add(quantity);
        Quantity remainingQuantity = current.submittedQuantity().subtract(filledQuantity);
        double averagePrice = (current.averageFillPrice() * current.filledQuantity().asDouble() + price * quantity.asDouble())
            / filledQuantity.asDouble();
        OrderStatus status = remainingQuantity.isZero() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        OrderState updated = new OrderState(orderId, status, current.submittedQuantity(), filledQuantity,
                remainingQuantity, averagePrice, current.timeInForce(), current.expireTimeNs());
        states.put(orderId, updated);
        return updated;
    }

    public OrderState expire(String orderId) {
        return transition(orderId, OrderStatus.EXPIRED, OrderStatus.ACCEPTED,
                OrderStatus.PARTIALLY_FILLED, OrderStatus.PENDING_CANCEL, OrderStatus.TRIGGERED,
                OrderStatus.EMULATED);
    }

    public OrderState voidOrder(String orderId) {
        return transition(orderId, OrderStatus.VOIDED, OrderStatus.FILLED);
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
        OrderStatus previousStatus = pendingPreviousStatuses.remove(current.orderId());
        OrderStatus restoredStatus = previousStatus == OrderStatus.TRIGGERED
            ? OrderStatus.TRIGGERED
            : current.filledQuantity().isZero() ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
        OrderState restored = new OrderState(current.orderId(), restoredStatus, current.submittedQuantity(),
                current.filledQuantity(), current.remainingQuantity(), current.averageFillPrice(),
                current.timeInForce(), current.expireTimeNs());
        states.put(current.orderId(), restored);
        return restored;
    }

    private static boolean isCancellable(OrderStatus status) {
        return status == OrderStatus.ACCEPTED || status == OrderStatus.TRIGGERED
            || status == OrderStatus.PENDING_UPDATE || status == OrderStatus.PARTIALLY_FILLED;
    }

    private static boolean isModifiable(OrderStatus status) {
        return status == OrderStatus.SUBMITTED || status == OrderStatus.ACCEPTED
            || status == OrderStatus.TRIGGERED || status == OrderStatus.PENDING_UPDATE
                || status == OrderStatus.PARTIALLY_FILLED;
    }
}