package com.abc.trading.events;

/** Result of replaying persisted events into the bus and state projection. */
public record EventReplayResult(
        EventReplayState state,
        long eventsReplayed,
        long nextOffset) {
    public EventReplayResult {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (eventsReplayed < 0 || nextOffset < 0) throw new IllegalArgumentException("replay values must be non-negative");
    }
}
