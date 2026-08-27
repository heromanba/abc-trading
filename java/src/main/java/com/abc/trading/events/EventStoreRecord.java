package com.abc.trading.events;

/** Versioned append-only event-store envelope. */
public record EventStoreRecord(
        int schemaVersion,
        long offset,
        Event event) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EventStoreRecord {
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        if (offset < 0) throw new IllegalArgumentException("offset must be non-negative");
        if (event == null) throw new IllegalArgumentException("event is required");
    }
}
