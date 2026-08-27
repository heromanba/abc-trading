package com.abc.trading.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Durable replay checkpoint keyed by event-store offset. */
public record EventCheckpoint(
        int schemaVersion,
        long nextOffset,
        long lastInputSequence,
        long lastLifecycleSequence) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EventCheckpoint {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) throw new IllegalArgumentException("unsupported checkpoint schema");
        if (nextOffset < 0 || lastInputSequence < 0 || lastLifecycleSequence < 0) {
            throw new IllegalArgumentException("checkpoint values must be non-negative");
        }
    }

    public static EventCheckpoint from(EventStoreRecord record) {
        return new EventCheckpoint(CURRENT_SCHEMA_VERSION, record.offset() + 1,
                record.event().inputSequence(), record.event().lifecycleSequence());
    }

    public void save(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
            mapper.writeValue(path.toFile(), this);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to save event checkpoint at " + path, error);
        }
    }

    public static EventCheckpoint load(Path path) {
        try {
            ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
            return mapper.readValue(path.toFile(), EventCheckpoint.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load event checkpoint at " + path, error);
        }
    }
}
