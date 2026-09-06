package com.abc.trading.execution;

import com.abc.trading.data.Quantity;
import com.abc.trading.msgbus.Handler;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.execution.commands.CancelOrder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Shared lifecycle, child-order, fill, cancellation, and recovery behavior. */
public abstract class ExecutionAlgorithm implements AutoCloseable {
    protected final MessageBus bus;
    protected final String algorithmId;
    protected final String symbol;
    protected final SignalDirection side;
    protected final Quantity targetQuantity;
    protected final TimeInForce timeInForce;

    private final Map<String, Quantity> childQuantities = new LinkedHashMap<>();
    private final Map<String, Quantity> childFills = new LinkedHashMap<>();
    private final Handler<OrderFill> fillHandler = this::onFill;
    private Quantity submittedQuantity;
    private Quantity filledQuantity;
    private long nextChildSequence;
    private long scheduleIndex;
    private BigDecimal observedVolume = BigDecimal.ZERO;
    private long lastTimestampNs;
    private boolean active;

    protected ExecutionAlgorithm(MessageBus bus, String algorithmId, String symbol,
            SignalDirection side, Quantity targetQuantity, TimeInForce timeInForce) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.algorithmId = requireText(algorithmId, "algorithmId");
        this.symbol = requireText(symbol, "symbol");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        this.side = side;
        this.targetQuantity = Objects.requireNonNull(targetQuantity, "targetQuantity");
        if (targetQuantity.isZero()) throw new IllegalArgumentException("targetQuantity is required");
        this.timeInForce = Objects.requireNonNull(timeInForce, "timeInForce");
        this.submittedQuantity = Quantity.fromDecimal(BigDecimal.ZERO, targetQuantity.precision());
        this.filledQuantity = Quantity.fromDecimal(BigDecimal.ZERO, targetQuantity.precision());
    }

    public final void start() {
        if (active) return;
        active = true;
        bus.subscribe(OrderFill.class, fillHandler, 100);
        onStart();
    }

    public final void cancel() {
        if (!active) return;
        active = false;
        for (String orderId : childQuantities.keySet()) {
            Quantity childFilled = childFills.getOrDefault(orderId,
                    Quantity.fromDecimal(BigDecimal.ZERO, targetQuantity.precision()));
            if (childFilled.compareTo(childQuantities.get(orderId)) < 0) {
                bus.publish(new CancelOrder(algorithmId, symbol, orderId,
                        algorithmId + "-cancel-" + nextChildSequence++, lastTimestampNs));
            }
        }
        onStop();
    }

    public final boolean isActive() { return active; }
    public final Quantity submittedQuantity() { return submittedQuantity; }
    public final Quantity filledQuantity() { return filledQuantity; }
    public final Quantity remainingQuantity() { return targetQuantity.subtract(submittedQuantity); }

    public final ExecutionAlgorithmState state() {
        return new ExecutionAlgorithmState(algorithmId, symbol, side, targetQuantity,
                submittedQuantity, filledQuantity, nextChildSequence, scheduleIndex,
                observedVolume, lastTimestampNs, active);
    }

    public final void restore(ExecutionAlgorithmState state) {
        if (!algorithmId.equals(state.algorithmId()) || !symbol.equals(state.symbol()) || side != state.side()
                || !targetQuantity.equals(state.targetQuantity())) {
            throw new IllegalArgumentException("execution state does not match algorithm");
        }
        submittedQuantity = state.submittedQuantity();
        filledQuantity = state.filledQuantity();
        nextChildSequence = state.nextChildSequence();
        scheduleIndex = state.scheduleIndex();
        observedVolume = state.observedVolume();
        lastTimestampNs = state.lastTimestampNs();
        if (active) throw new IllegalStateException("cannot restore a running algorithm");
        if (state.active()) start();
    }

    protected final boolean canSubmit() {
        return active && !remainingQuantity().isZero();
    }

    protected final String submitChild(Quantity quantity, double price, long timestampNs) {
        if (!canSubmit() || quantity == null || quantity.isZero()) return null;
        Quantity slice = quantity.min(remainingQuantity());
        String orderId = algorithmId + "-child-" + nextChildSequence++;
        childQuantities.put(orderId, slice);
        childFills.put(orderId, Quantity.fromDecimal(BigDecimal.ZERO, targetQuantity.precision()));
        submittedQuantity = submittedQuantity.add(slice);
        lastTimestampNs = timestampNs;
        bus.publish(new OrderIntent(algorithmId, symbol, nextChildSequence, timestampNs,
                orderId + "-corr", orderId, side, slice, price, BigDecimal.ZERO, 0.0,
                timeInForce, 0L, 0.0, TriggerType.NO_TRIGGER));
        if (remainingQuantity().isZero()) onTargetSubmitted();
        return orderId;
    }

    protected final void advanceSchedule() { scheduleIndex++; }
    protected final long scheduleIndex() { return scheduleIndex; }
    protected final void observeVolume(BigDecimal volume) { observedVolume = observedVolume.add(volume); }
    protected final BigDecimal observedVolume() { return observedVolume; }
    protected final long lastTimestampNs() { return lastTimestampNs; }
    protected final void setLastTimestampNs(long timestampNs) { lastTimestampNs = timestampNs; }
    protected final void setScheduleIndex(long index) { scheduleIndex = index; }
    protected final void setObservedVolume(BigDecimal volume) { observedVolume = volume; }

    protected abstract void onStart();
    protected abstract void onStop();
    protected void onTargetSubmitted() { }

    @Override
    public final void close() {
        if (active) cancel();
        else onStop();
        bus.unsubscribe(OrderFill.class, fillHandler);
    }

    private void onFill(OrderFill fill) {
        if (!algorithmId.equals(fill.strategyId()) || !symbol.equals(fill.symbol())) return;
        Quantity childQuantity = childQuantities.get(fill.orderId());
        if (childQuantity == null) return;
        Quantity childFilled = childFills.get(fill.orderId()).add(fill.quantity());
        childFills.put(fill.orderId(), childFilled);
        filledQuantity = filledQuantity.add(fill.quantity());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
