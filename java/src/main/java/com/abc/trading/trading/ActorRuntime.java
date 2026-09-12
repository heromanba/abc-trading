package com.abc.trading.trading;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Consumer;

/** Deterministic actor runtime with bounded mailbox, virtual timers, restart, and fault recovery. */
public final class ActorRuntime<M> implements AutoCloseable {
    private final String actorId;
    private final ActorLifecycle<M> actor;
    private final int mailboxCapacity;
    private final Queue<M> mailbox = new ArrayDeque<>();
    private final PriorityQueue<ScheduledTask<M>> scheduled = new PriorityQueue<>(
            Comparator.comparingLong((ScheduledTask<M> task) -> task.timestampNs())
                .thenComparingLong(task -> task.sequence()));
    private final List<ActorEvent> events = new ArrayList<>();
    private final Consumer<ActorEvent> eventSink;
    private long sequence;
    private long clockNs;
    private boolean running;
    private boolean faulted;

    public ActorRuntime(String actorId, ActorLifecycle<M> actor, int mailboxCapacity) {
        this(actorId, actor, mailboxCapacity, event -> { });
    }

    public ActorRuntime(String actorId, ActorLifecycle<M> actor, int mailboxCapacity,
            Consumer<ActorEvent> eventSink) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (mailboxCapacity < 1) throw new IllegalArgumentException("mailboxCapacity must be positive");
        this.actorId = actorId;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.mailboxCapacity = mailboxCapacity;
        this.eventSink = eventSink == null ? event -> { } : eventSink;
    }

    public synchronized void start(long timestampNs) {
        if (faulted) throw new IllegalStateException("actor is faulted");
        if (running) return;
        clockNs = Math.max(clockNs, timestampNs);
        running = true;
        actor.onStart(this);
        emit(ActorEventType.STARTED, null);
        dispatchDue();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        actor.onStop();
        emit(ActorEventType.STOPPED, null);
    }

    public synchronized void reset(long timestampNs) {
        stop();
        mailbox.clear();
        scheduled.clear();
        faulted = false;
        clockNs = timestampNs;
        actor.onReset();
        emit(ActorEventType.RESET, null);
    }

    public synchronized void restart(long timestampNs) {
        reset(timestampNs);
        start(timestampNs);
        emit(ActorEventType.RESTARTED, null);
    }

    public synchronized boolean send(M message) {
        if (!running || faulted) return false;
        if (mailbox.size() >= mailboxCapacity) {
            emit(ActorEventType.MAILBOX_OVERFLOW, null);
            return false;
        }
        mailbox.add(Objects.requireNonNull(message, "message"));
        dispatchDue();
        return true;
    }

    public synchronized long schedule(long timestampNs, M message) {
        if (timestampNs < clockNs) throw new IllegalArgumentException("scheduled time must not be in the past");
        long taskSequence = sequence++;
        scheduled.add(new ScheduledTask<>(timestampNs, taskSequence, Objects.requireNonNull(message, "message")));
        emit(ActorEventType.TASK_SCHEDULED, null);
        return taskSequence;
    }

    public synchronized long scheduleAfter(long delayNs, M message) {
        if (delayNs < 0) throw new IllegalArgumentException("delayNs must be non-negative");
        return schedule(clockNs + delayNs, message);
    }

    public synchronized void advanceTo(long timestampNs) {
        if (timestampNs < clockNs) throw new IllegalArgumentException("clock cannot move backwards");
        clockNs = timestampNs;
        dispatchDue();
    }

    public synchronized boolean isRunning() { return running; }
    public synchronized boolean isFaulted() { return faulted; }
    public synchronized long timestampNs() { return clockNs; }
    public synchronized int mailboxSize() { return mailbox.size(); }
    public synchronized List<ActorEvent> events() { return List.copyOf(events); }

    private void dispatchDue() {
        if (!running || faulted) return;
        while (!scheduled.isEmpty() && scheduled.peek().timestampNs() <= clockNs) {
            if (mailbox.size() >= mailboxCapacity) {
                emit(ActorEventType.MAILBOX_OVERFLOW, null);
                scheduled.remove();
            } else {
                mailbox.add(scheduled.remove().message());
                emit(ActorEventType.TIMER_FIRED, null);
            }
        }
        while (!mailbox.isEmpty() && running && !faulted) {
            M message = mailbox.remove();
            try {
                actor.onMessage(message, this);
                moveDueTimers();
            } catch (Throwable failure) {
                faulted = true;
                running = false;
                emit(ActorEventType.FAULTED, failure);
                actor.onFault(failure);
            }
        }
    }

    private void moveDueTimers() {
        while (!scheduled.isEmpty() && scheduled.peek().timestampNs() <= clockNs) {
            if (mailbox.size() >= mailboxCapacity) {
                emit(ActorEventType.MAILBOX_OVERFLOW, null);
                scheduled.remove();
            } else {
                mailbox.add(scheduled.remove().message());
                emit(ActorEventType.TIMER_FIRED, null);
            }
        }
    }

    private void emit(ActorEventType type, Throwable failure) {
        ActorEvent event = new ActorEvent(type, actorId, clockNs, failure);
        events.add(event);
        eventSink.accept(event);
    }

    @Override
    public synchronized void close() {
        stop();
        mailbox.clear();
        scheduled.clear();
    }

    private record ScheduledTask<M>(long timestampNs, long sequence, M message) { }

    public interface ActorLifecycle<M> {
        default void onStart(ActorRuntime<M> runtime) { }
        default void onStop() { }
        default void onReset() { }
        default void onMessage(M message, ActorRuntime<M> runtime) { }
        default void onFault(Throwable failure) { }
    }
}
