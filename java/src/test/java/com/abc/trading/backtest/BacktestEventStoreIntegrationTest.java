package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.events.EventStoreRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEventStoreIntegrationTest {
    @Test
    void backtestWritesCanonicalEventsToCsvAndJsonl(@TempDir Path tempDir) {
        Path csvPath = tempDir.resolve("events.csv");
        Path storePath = tempDir.resolve("events.jsonl");
        BacktestEngine engine = new BacktestEngine(csvPath.toString(), storePath.toString());
        engine.addVenue("XNAS");
        engine.addInstrument("AAPL", "XNAS");
        engine.start();
        engine.runBars(new Bar[] { new Bar("AAPL", 100, 100.0, 1) });
        engine.submitMarketOrder("store-test", "AAPL", "order-1", SignalDirection.BUY, 1, 100, 100.0);
        engine.close();

        try (com.abc.trading.events.PersistentEventStore store =
                     new com.abc.trading.events.PersistentEventStore(storePath)) {
            assertTrue(store.readRecords().size() >= 2);
            assertTrue(store.readRecords().stream()
                    .allMatch(record -> record.schemaVersion() == EventStoreRecord.CURRENT_SCHEMA_VERSION));
        }
    }
}
