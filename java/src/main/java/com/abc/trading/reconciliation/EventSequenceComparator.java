package com.abc.trading.reconciliation;

import com.abc.trading.events.Event;
import com.abc.trading.events.EventStoreRecord;
import com.abc.trading.events.PersistentEventStore;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Compares complete persisted lifecycle sequences with exact decimal normalization. */
public final class EventSequenceComparator {
    public ReconciliationResult compare(Path expectedPath, Path actualPath) {
        List<EventStoreRecord> expected = read(expectedPath);
        List<EventStoreRecord> actual = read(actualPath);
        if (expected.size() != actual.size()) {
            return ReconciliationResult.mismatch(Math.min(expected.size(), actual.size()) + 1,
                    "event count differs: expected=" + expected.size() + ", actual=" + actual.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            Event left = expected.get(index).event();
            Event right = actual.get(index).event();
            String mismatch = compareEvent(left, right);
            if (mismatch != null) return ReconciliationResult.mismatch(index + 1, mismatch);
        }
        return ReconciliationResult.matched(expected.size());
    }

    private static List<EventStoreRecord> read(Path path) {
        try (PersistentEventStore store = new PersistentEventStore(path)) {
            return store.readRecords();
        }
    }

    private static String compareEvent(Event expected, Event actual) {
        if (expected.eventType() != actual.eventType()) return "eventType differs";
        if (!Objects.equals(expected.symbol(), actual.symbol())) return "symbol differs";
        if (!Objects.equals(expected.orderId(), actual.orderId())) return "orderId differs";
        if (expected.signalDirection() != actual.signalDirection()) return "signalDirection differs";
        if (!decimalEquals(expected.currentPosition(), actual.currentPosition())) return "position differs";
        if (!decimalEquals(expected.realizedPnl(), actual.realizedPnl())) return "realizedPnl differs";
        if (!decimalEquals(expected.commission(), actual.commission())) return "commission differs";
        if (!decimalEquals(BigDecimal.valueOf(expected.price()), BigDecimal.valueOf(actual.price()))) return "price differs";
        if (!expected.quantity().equals(actual.quantity())) return "quantity differs";
        if (expected.marketTimestamp() != actual.marketTimestamp()) return "marketTimestamp differs";
        return null;
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
