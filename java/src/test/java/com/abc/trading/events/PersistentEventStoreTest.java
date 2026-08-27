package com.abc.trading.events;

import com.abc.trading.execution.LiquiditySide;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.msgbus.MessageBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistentEventStoreTest {
    @Test
    void appendsVersionedRecordsAndResumesFromCheckpoint(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("events.jsonl");
        Event submit = new Event(1, 1, 100, "AAPL", "OrderIntent", EventType.ORDER_SUBMIT,
                "strategy", SignalDirection.BUY, "corr", "order-1", 100.0, 2, 0, 0.0);
        Event fill = new Event(1, 2, 101, "AAPL", "SettledOrderFill", EventType.ORDER_FILL,
                "strategy", SignalDirection.BUY, "corr", "order-1", 100.0, 2, 2, 0.0,
                0.0, "USD", LiquiditySide.TAKER, "venue-fill-1");
        Event account = new Event(0, 3, 101, "", "AccountStateEvent", EventType.ACCOUNT_STATE,
                "", SignalDirection.HOLD, "", "", 0.0, 0, 0, 0.0, 0.0, "USD", null, "",
                "USD", 1000.0, 100.0, 900.0, 100.0, 50.0, 0.0, 1000.0, false, false);

        try (PersistentEventStore store = new PersistentEventStore(storePath)) {
            store.log(submit);
            store.log(fill);
            store.log(account);
        }

        AtomicInteger delivered = new AtomicInteger();
        MessageBus bus = new MessageBus(null);
        bus.subscribe(Event.class, event -> delivered.incrementAndGet());
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        EventCheckpoint.from(new PersistentEventStore(storePath).readRecords().get(0)).save(checkpointPath);

        EventReplayResult result = EventReplayer.replay(storePath, bus, checkpointPath);

        assertEquals(2, result.eventsReplayed());
        assertEquals(3, result.nextOffset());
        assertEquals(2, delivered.get());
        assertEquals(2, result.state().orders().get("order-1").filledQuantity());
        assertEquals(2, result.state().positions().get("AAPL"));
        assertEquals(900.0, result.state().accounts().get("USD").free(), 1e-9);
        assertEquals(3, EventCheckpoint.load(checkpointPath).nextOffset());
    }

    @Test
    void reopensStoreWithNextOffsetAndRejectsCorruptOffset(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("events.jsonl");
        Event event = new Event(1, 1, 100, "AAPL", "OrderIntent", EventType.ORDER_SUBMIT,
                "strategy", SignalDirection.BUY, "corr", "order-1", 100.0, 1, 0, 0.0);
        try (PersistentEventStore store = new PersistentEventStore(storePath)) {
            store.log(event);
        }
        try (PersistentEventStore store = new PersistentEventStore(storePath)) {
            store.log(event);
        }
        assertEquals(2, new PersistentEventStore(storePath).readRecords().size());

        Files.writeString(storePath, Files.readString(storePath).replace("\"offset\":1", "\"offset\":9"));
        assertThrows(IllegalStateException.class, () -> new PersistentEventStore(storePath).readRecords());
    }
}
