package com.abc.trading.events;

import java.util.List;

/** Sends each canonical event to multiple durable or diagnostic sinks. */
public final class CompositeEventLogger implements EventLogger {
    private final List<EventLogger> delegates;

    public CompositeEventLogger(List<EventLogger> delegates) {
        if (delegates == null || delegates.isEmpty()) throw new IllegalArgumentException("delegates are required");
        if (delegates.stream().anyMatch(delegate -> delegate == null)) throw new IllegalArgumentException("delegate is required");
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void log(Event event) {
        for (EventLogger delegate : delegates) delegate.log(event);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (EventLogger delegate : delegates) {
            try {
                delegate.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
