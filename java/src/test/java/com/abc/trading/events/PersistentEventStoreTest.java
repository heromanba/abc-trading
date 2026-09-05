package com.abc.trading.events;

import com.abc.trading.data.Quantity;
import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(Quantity.fromInt(2), result.state().orders().get("order-1").filledQuantity());
        assertEquals(BigDecimal.valueOf(2), result.state().positions().get("AAPL"));
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

    @Test
    void roundTripsCanonicalDecimalStringsWithoutDoubleConversion(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("precise-events.jsonl");
        Event event = new Event(7, 9, 100, "BTCUSDT", "SettledOrderFill", EventType.ORDER_FILL,
                "strategy", SignalDirection.BUY, "corr", "precise-order", 100.1,
                Quantity.fromString("0.001", 3), new BigDecimal("0.001"),
                new BigDecimal("1234567890.123456789"), new BigDecimal("0.00000001"),
                "USDT", LiquiditySide.TAKER, "venue-fill", "USDT",
                new BigDecimal("1000000000.00000001"), new BigDecimal("0.00000001"),
                new BigDecimal("999999999.99999999"), new BigDecimal("0.00000001"),
                new BigDecimal("0.000000005"), new BigDecimal("-0.000000001"),
                new BigDecimal("1234567889.123456788"), false, false);

        try (PersistentEventStore store = new PersistentEventStore(storePath)) {
            store.log(event);
        }

        String json = Files.readString(storePath);
        assertTrue(json.contains("\"quantity\":\"0.001\""));
        assertTrue(json.contains("\"realizedPnl\":\"1234567890.123456789\""));
        Event restored = new PersistentEventStore(storePath).readEvents().get(0);
        assertEquals(event.quantity(), restored.quantity());
        assertEquals(event.realizedPnl(), restored.realizedPnl());
        assertEquals(event.accountTotal(), restored.accountTotal());
    }

    @Test
    void writesCanonicalDecimalMoneyToCsv(@TempDir Path tempDir) throws Exception {
        Path csvPath = tempDir.resolve("precise-events.csv");
        Event event = new Event(1, 1, 100, "BTCUSDT", "AccountStateEvent", EventType.ACCOUNT_STATE,
                "strategy", SignalDirection.HOLD, "corr", "", 100.1,
                Quantity.fromInt(0), BigDecimal.ZERO, new BigDecimal("0.000000001"),
                new BigDecimal("0.00000001"), "USDT", null, "", "USDT",
                new BigDecimal("1000.00500001"), new BigDecimal("0.00000001"),
                new BigDecimal("1000.00500000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-0.000000001"), new BigDecimal("1000.004999999"), false, false);

        try (CsvEventLogger logger = new CsvEventLogger(csvPath)) {
            logger.log(event);
        }

        String row = Files.readAllLines(csvPath).get(1);
        assertTrue(row.contains(",0.000000001,0.00000001,USDT,"));
        assertTrue(row.contains(",1000.00500001,0.00000001,1000.00500000,"));
    }

    @Test
    void replaysFundingPaymentBeforeItsPostSettlementAccountState(@TempDir Path tempDir) {
        Path storePath = tempDir.resolve("funding-events.jsonl");
        Event funding = new Event(0, 1, 200, "PERP", "FundingPayment",
                EventType.FUNDING_PAYMENT, "", SignalDirection.HOLD, "", "", 100.0,
                Quantity.fromInt(0), new BigDecimal("1"), new BigDecimal("-1.000"),
                BigDecimal.ZERO, "USDT", null, "");
        Event account = new Event(0, 2, 200, "", "AccountStateEvent", EventType.ACCOUNT_STATE,
                "", SignalDirection.HOLD, "", "", 0.0, 0, 0, 0.0, 0.0, "USDT", null, "",
                "USDT", 999.0, 100.0, 899.0, 100.0, 50.0, 0.0, 999.0, false, false);

        try (PersistentEventStore store = new PersistentEventStore(storePath)) {
            store.log(funding);
            store.log(account);
        }

        EventReplayResult result = EventReplayer.replay(storePath, new MessageBus(null));

        assertEquals(2, result.eventsReplayed());
        assertEquals(new BigDecimal("1"), result.state().positions().get("PERP"));
        assertEquals(new BigDecimal("-1.000"), result.state().realizedPnl().get("PERP"));
        assertEquals(999.0, result.state().accounts().get("USDT").total(), 1e-9);
    }
}
