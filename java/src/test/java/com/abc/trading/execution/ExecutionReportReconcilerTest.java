package com.abc.trading.execution;

import com.abc.trading.data.Price;
import com.abc.trading.data.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionReportReconcilerTest {
    @Test
    void rejectsDuplicatesAndOutOfOrderReports() {
        ExecutionReportReconciler reconciler = new ExecutionReportReconciler();
        ExecutionReport accepted = report("NEW", "ACCEPTED", 1000, "new");
        ExecutionReport duplicate = report("NEW", "ACCEPTED", 1000, "new");
        ExecutionReport stale = report("CANCELED", "CANCELED", 900, "cancel-old");

        assertTrue(reconciler.accept(accepted).isPresent());
        assertTrue(reconciler.accept(duplicate).isEmpty());
        assertTrue(reconciler.accept(stale).isEmpty());
        assertEquals(OrderStatus.ACCEPTED, reconciler.status("client-1"));
        assertEquals(2, reconciler.seenReportCount());
    }

    @Test
    void acceptsPartialFillThenFilledAndRejectsLateCancelAcknowledgement() {
        ExecutionReportReconciler reconciler = new ExecutionReportReconciler();

        assertTrue(reconciler.accept(report("TRADE", "PARTIALLY_FILLED", 1000, "fill-1")).isPresent());
        assertTrue(reconciler.accept(report("TRADE", "FILLED", 1100, "fill-2")).isPresent());
        assertTrue(reconciler.accept(report("CANCELED", "CANCELED", 1200, "cancel-late")).isEmpty());
        assertEquals(OrderStatus.FILLED, reconciler.status("client-1"));
    }

    private static ExecutionReport report(String executionType, String status, long time, String key) {
        return new ExecutionReport("client-1", "server-1", "BTCUSDT",
                status.equals("PARTIALLY_FILLED") ? OrderEventType.PARTIALLY_FILLED
                        : status.equals("FILLED") ? OrderEventType.FILLED
                        : status.equals("CANCELED") ? OrderEventType.CANCELED : OrderEventType.ACCEPTED,
                OrderStatus.valueOf(status), SignalDirection.BUY, Quantity.fromInt(1),
                Price.fromString("100.00", 2), BigDecimal.ZERO, "USDT", time, key);
    }
}
