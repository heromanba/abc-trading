package com.abc.trading.execution;

import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class OrderStateMachine {
    private final Map<String, OrderState> states = new LinkedHashMap<>();
    private final Map<String, OrderStatus> pendingPreviousStatuses = new LinkedHashMap<>();
    private final Map<String, String> pendingUpdateCommands = new LinkedHashMap<>();
    private final List<OrderEvent> events = new ArrayList<>();
    private final Consumer<OrderEvent> eventSink;

    public OrderStateMachine() {
        this(event -> { });
    }

    public OrderStateMachine(Consumer<OrderEvent> eventSink) {
        this.eventSink = eventSink == null ? event -> { } : eventSink;
    }

    public OrderState initialize(String orderId, int quantity, TimeInForce timeInForce, long expireTimeNs) {
        return initialize(orderId, Quantity.fromInt(quantity), timeInForce, expireTimeNs);
    }

    public OrderState initialize(String orderId, Quantity quantity, TimeInForce timeInForce, long expireTimeNs) {
        if (states.containsKey(orderId)) throw new IllegalStateException("Duplicate order: " + orderId);
        OrderState state = new OrderState(orderId, OrderStatus.INITIALIZED, quantity, Quantity.fromInt(0), quantity,
                0.0, timeInForce, expireTimeNs);
        states.put(orderId, state);
        emit(orderId, OrderEventType.INITIALIZED, null, state);
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
        pendingUpdateCommands.remove(orderId);
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
        return pendingUpdate(orderId, "legacy-update-" + orderId);
    }

    public OrderState pendingUpdate(String orderId, String commandId) {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        OrderState current = state(orderId);
        if (!isModifiable(current.status())) {
            throw new IllegalStateException("Cannot modify order from " + current.status());
        }
        pendingPreviousStatuses.put(orderId, current.status());
        pendingUpdateCommands.put(orderId, commandId);
        return transition(orderId, OrderStatus.PENDING_UPDATE, current.status());
    }

    public OrderState update(String orderId, int quantity) {
        return update(orderId, Quantity.fromInt(quantity));
    }

    public OrderState update(String orderId, Quantity quantity) {
        return update(orderId, quantity, pendingUpdateCommands.get(orderId));
    }

    public OrderState update(String orderId, Quantity quantity, String commandId) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_UPDATE) {
            throw new IllegalStateException("Cannot update order from " + current.status());
        }
        if (commandId == null || !commandId.equals(pendingUpdateCommands.get(orderId))) {
            throw new IllegalStateException("stale modify acknowledgement for " + orderId);
        }
        if (quantity.compareTo(current.filledQuantity()) < 0 || quantity.isZero()) {
            throw new IllegalArgumentException("updated quantity must cover existing fills");
        }
        OrderStatus previousStatus = pendingPreviousStatuses.remove(orderId);
        pendingUpdateCommands.remove(orderId);
        OrderStatus updatedStatus = previousStatus == OrderStatus.TRIGGERED
            ? OrderStatus.TRIGGERED
            : current.filledQuantity().isZero() ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
        OrderState updated = new OrderState(orderId, updatedStatus, quantity,
                current.filledQuantity(), quantity.subtract(current.filledQuantity()), current.averageFillPrice(),
                current.timeInForce(), current.expireTimeNs());
        states.put(orderId, updated);
        emit(orderId, OrderEventType.UPDATED, current.status(), updated);
        return updated;
    }

    public OrderState updateReject(String orderId) {
        return updateReject(orderId, pendingUpdateCommands.get(orderId));
    }

    public OrderState updateReject(String orderId, String commandId) {
        OrderState current = state(orderId);
        if (current.status() != OrderStatus.PENDING_UPDATE) {
            throw new IllegalStateException("Cannot reject update from " + current.status());
        }
        if (commandId == null || !commandId.equals(pendingUpdateCommands.get(orderId))) {
            throw new IllegalStateException("stale modify rejection for " + orderId);
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
        emit(orderId, remainingQuantity.isZero() ? OrderEventType.FILLED : OrderEventType.PARTIALLY_FILLED,
            current.status(), updated);
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
                emit(orderId, eventType(target), current.status(), updated);
                return updated;
            }
        }
        throw new IllegalStateException("Cannot transition order from " + current.status() + " to " + target);
    }

    private OrderState restoreOpenState(OrderState current) {
        OrderStatus previousStatus = pendingPreviousStatuses.remove(current.orderId());
        pendingUpdateCommands.remove(current.orderId());
        OrderStatus restoredStatus = previousStatus == OrderStatus.TRIGGERED
            ? OrderStatus.TRIGGERED
            : current.filledQuantity().isZero() ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
        OrderState restored = new OrderState(current.orderId(), restoredStatus, current.submittedQuantity(),
                current.filledQuantity(), current.remainingQuantity(), current.averageFillPrice(),
                current.timeInForce(), current.expireTimeNs());
        states.put(current.orderId(), restored);
        emit(current.orderId(), current.status() == OrderStatus.PENDING_CANCEL
                ? OrderEventType.CANCEL_REJECTED : OrderEventType.UPDATE_REJECTED, current.status(), restored);
        return restored;
    }

    public List<OrderEvent> events() {
        return List.copyOf(events);
    }

    private void emit(String orderId, OrderEventType type, OrderStatus previous, OrderState state) {
        OrderEvent event = new OrderEvent(orderId, "", type, previous, state.status(), state.submittedQuantity(),
                state.filledQuantity(), state.remainingQuantity(), Price.fromDouble(state.averageFillPrice()));
        events.add(event);
        eventSink.accept(event);
    }

    private static OrderEventType eventType(OrderStatus status) {
        return switch (status) {
            case INITIALIZED -> OrderEventType.INITIALIZED;
            case SUBMITTED -> OrderEventType.SUBMITTED;
            case EMULATED -> OrderEventType.EMULATED;
            case RELEASED -> OrderEventType.RELEASED;
            case ACCEPTED -> OrderEventType.ACCEPTED;
            case TRIGGERED -> OrderEventType.TRIGGERED;
            case DENIED -> OrderEventType.DENIED;
            case REJECTED -> OrderEventType.REJECTED;
            case PENDING_CANCEL -> OrderEventType.PENDING_CANCEL;
            case CANCELED -> OrderEventType.CANCELED;
            case PENDING_UPDATE -> OrderEventType.PENDING_UPDATE;
            case PARTIALLY_FILLED -> OrderEventType.PARTIALLY_FILLED;
            case FILLED -> OrderEventType.FILLED;
            case EXPIRED -> OrderEventType.EXPIRED;
            case VOIDED -> OrderEventType.VOIDED;
        };
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