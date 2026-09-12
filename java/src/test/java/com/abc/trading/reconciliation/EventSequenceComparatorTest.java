package com.abc.trading.reconciliation;

import com.abc.trading.events.Event;
import com.abc.trading.events.EventType;
import com.abc.trading.events.PersistentEventStore;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.data.Quantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSequenceComparatorTest {
    @Test
    void comparesCompleteSequencesWithDecimalScaleNormalization(@TempDir Path tempDir) {
        Path expected = tempDir.resolve("expected.jsonl");
        Path actual = tempDir.resolve("actual.jsonl");
        Event expectedEvent = new Event(1, 1, 100, "BTCUSDT", "Fill", EventType.ORDER_FILL,
                "strategy", SignalDirection.BUY, "corr", "order", 100.0,
                Quantity.fromInt(1), new BigDecimal("1.00"), new BigDecimal("0.100"));
        Event actualEvent = new Event(1, 1, 100, "BTCUSDT", "Fill", EventType.ORDER_FILL,
                "strategy", SignalDirection.BUY, "corr", "order", 100.00,
                Quantity.fromInt(1), new BigDecimal("1.0"), new BigDecimal("0.1"));
        write(expected, expectedEvent);
        write(actual, actualEvent);

        ReconciliationResult result = new EventSequenceComparator().compare(expected, actual);

        assertTrue(result.matched());
        assertEquals(1, result.comparedRows());
    }

    @Test
    void reportsFirstFullEventMismatch(@TempDir Path tempDir) {
        Path expected = tempDir.resolve("expected.jsonl");
        Path actual = tempDir.resolve("actual.jsonl");
        write(expected, event(EventType.ORDER_ACCEPT, "AAPL"));
        write(actual, event(EventType.ORDER_REJECT, "AAPL"));

        ReconciliationResult result = new EventSequenceComparator().compare(expected, actual);

        assertFalse(result.matched());
        assertEquals(1, result.comparedRows());
        assertTrue(result.mismatch().contains("eventType"));
    }

    private static Event event(EventType type, String symbol) {
        return new Event(1, 1, 100, symbol, type.name(), type, "strategy", SignalDirection.BUY,
                "corr", "order", 100.0, Quantity.fromInt(1), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static void write(Path path, Event event) {
        try (PersistentEventStore store = new PersistentEventStore(path)) {
            store.log(event);
        }
    }
}
