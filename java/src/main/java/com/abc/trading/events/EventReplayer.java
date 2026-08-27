package com.abc.trading.events;

import com.abc.trading.msgbus.MessageBus;

import java.nio.file.Path;
import java.util.List;

/** Replays versioned events into the synchronous bus and a deterministic state projection. */
public final class EventReplayer {
    private EventReplayer() { }

    public static EventReplayResult replay(Path eventStorePath, MessageBus bus) {
        return replay(eventStorePath, bus, null);
    }

    public static EventReplayResult replay(Path eventStorePath, MessageBus bus, Path checkpointPath) {
        if (eventStorePath == null) throw new IllegalArgumentException("eventStorePath is required");
        if (bus == null) throw new IllegalArgumentException("bus is required");
        try (PersistentEventStore store = new PersistentEventStore(eventStorePath)) {
            List<EventStoreRecord> records = store.readRecords();
            EventCheckpoint checkpoint = checkpointPath != null && java.nio.file.Files.exists(checkpointPath)
                    ? EventCheckpoint.load(checkpointPath) : null;
            long startOffset = checkpoint == null ? 0 : checkpoint.nextOffset();
            if (startOffset > records.size()) {
                throw new IllegalStateException("Checkpoint offset exceeds event-store length");
            }
            EventReplayState state = new EventReplayState();
            long delivered = 0;
            for (EventStoreRecord record : records) {
                state.apply(record.event());
                if (record.offset() < startOffset) continue;
                bus.publish(record.event());
                delivered++;
                if (checkpointPath != null) EventCheckpoint.from(record).save(checkpointPath);
            }
            return new EventReplayResult(state, delivered, records.size());
        }
    }
}
