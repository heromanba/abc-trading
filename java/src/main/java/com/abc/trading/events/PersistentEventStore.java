package com.abc.trading.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Append-only JSON Lines event store with versioned envelopes. */
public final class PersistentEventStore implements EventLogger {
    private final Path path;
    private final ObjectMapper mapper;
    private final BufferedWriter writer;
    private long nextOffset;

    public PersistentEventStore(Path path) {
        try {
            this.path = path;
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            this.mapper = JsonMapper.builder().findAndAddModules().build();
            this.nextOffset = countRecords(path);
            this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to open event store at " + path, error);
        }
    }

    public Path path() {
        return path;
    }

    @Override
    public synchronized void log(Event event) {
        try {
            EventStoreRecord record = new EventStoreRecord(
                    EventStoreRecord.CURRENT_SCHEMA_VERSION, nextOffset++, event);
            writer.write(mapper.writeValueAsString(record));
            writer.newLine();
            writer.flush();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to append event", error);
        }
    }

    public synchronized List<EventStoreRecord> readRecords() {
        List<EventStoreRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            long expectedOffset = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                EventStoreRecord record = mapper.readValue(line, EventStoreRecord.class);
                validateRecord(record, expectedOffset++);
                records.add(record);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read event store at " + path, error);
        }
        return List.copyOf(records);
    }

    public synchronized List<Event> readEvents() {
        return readRecords().stream().map(EventStoreRecord::event).toList();
    }

    @Override
    public synchronized void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to close event store", error);
        }
    }

    private static long countRecords(Path path) throws IOException {
        if (!Files.exists(path)) return 0;
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).count();
        }
    }

    private static void validateRecord(EventStoreRecord record, long expectedOffset) {
        if (record.schemaVersion() != EventStoreRecord.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported event-store schema version: " + record.schemaVersion());
        }
        if (record.offset() != expectedOffset) {
            throw new IllegalStateException("Event-store offset mismatch: expected "
                    + expectedOffset + " but found " + record.offset());
        }
    }
}
