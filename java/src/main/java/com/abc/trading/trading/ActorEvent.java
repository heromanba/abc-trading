package com.abc.trading.trading;

public record ActorEvent(ActorEventType type, String actorId, long timestampNs, Throwable failure) {
    public ActorEvent {
        if (type == null || actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actor event identity is required");
        }
    }
}
