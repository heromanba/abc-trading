package com.abc.trading.events;

import com.abc.trading.execution.OrderStatus;
import com.abc.trading.data.Quantity;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory projection rebuilt while replaying the persistent event store. */
public final class EventReplayState {
    private final Map<String, ReplayOrderState> orders = new LinkedHashMap<>();
    private final Map<String, Integer> positions = new LinkedHashMap<>();
    private final Map<String, Double> realizedPnl = new LinkedHashMap<>();
    private final Map<String, ReplayAccountState> accounts = new LinkedHashMap<>();
    private long lastInputSequence;
    private long lastLifecycleSequence;

    public void apply(Event event) {
        lastInputSequence = Math.max(lastInputSequence, event.inputSequence());
        lastLifecycleSequence = Math.max(lastLifecycleSequence, event.lifecycleSequence());
        if (event.orderId() != null && !event.orderId().isBlank()) applyOrder(event);
        if (event.symbol() != null && !event.symbol().isBlank()) {
            positions.put(event.symbol(), event.currentPosition());
            realizedPnl.put(event.symbol(), event.realizedPnl());
        }
        if (event.accountCurrency() != null && !event.accountCurrency().isBlank()) {
            accounts.put(event.accountCurrency(), new ReplayAccountState(
                    event.accountCurrency(), event.accountTotal(), event.accountLocked(), event.accountFree(),
                    event.marginInitial(), event.marginMaintenance(), event.unrealizedPnl(), event.equity(),
                    event.marginCall(), event.liquidationRequired(), event.lifecycleSequence()));
        }
    }

    public Map<String, ReplayOrderState> orders() {
        return Map.copyOf(orders);
    }

    public Map<String, Integer> positions() {
        return Map.copyOf(positions);
    }

    public Map<String, Double> realizedPnl() {
        return Map.copyOf(realizedPnl);
    }

    public Map<String, ReplayAccountState> accounts() {
        return Map.copyOf(accounts);
    }

    public long lastInputSequence() {
        return lastInputSequence;
    }

    public long lastLifecycleSequence() {
        return lastLifecycleSequence;
    }

    private void applyOrder(Event event) {
        ReplayOrderState previous = orders.get(event.orderId());
        Quantity submitted = previous == null ? event.quantity() : previous.submittedQuantity();
        Quantity filled = previous == null ? Quantity.fromInt(0) : previous.filledQuantity();
        double average = previous == null ? 0.0 : previous.averageFillPrice();
        OrderStatus status = previous == null ? statusFor(event.eventType()) : previous.status();
        if (event.eventType() == EventType.ORDER_FILL || event.eventType() == EventType.LIQUIDATION_FILL) {
                Quantity nextFilled = filled.add(event.quantity());
                average = nextFilled.isZero() ? 0.0
                    : (average * filled.asDouble() + event.price() * event.quantity().asDouble()) / nextFilled.asDouble();
            filled = nextFilled;
                submitted = submitted.max(filled);
                status = filled.compareTo(submitted) >= 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        } else {
            OrderStatus nextStatus = statusFor(event.eventType());
            if (nextStatus != null) status = nextStatus;
        }
        orders.put(event.orderId(), new ReplayOrderState(event.orderId(), status, submitted, filled,
                submitted.subtract(filled), average, event.lifecycleSequence()));
    }

    private static OrderStatus statusFor(EventType eventType) {
        return switch (eventType) {
            case ORDER_SUBMIT -> OrderStatus.SUBMITTED;
            case ORDER_ACCEPT, ORDER_LIMIT_ACCEPT -> OrderStatus.ACCEPTED;
            case ORDER_DENY -> OrderStatus.DENIED;
            case ORDER_REJECT -> OrderStatus.REJECTED;
            case ORDER_CANCEL -> OrderStatus.CANCELED;
            case ORDER_EXPIRE -> OrderStatus.EXPIRED;
            case ORDER_TRIGGER -> OrderStatus.TRIGGERED;
            default -> null;
        };
    }
}
