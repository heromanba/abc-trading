package com.abc.trading.execution;

import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;
import com.abc.trading.system.NautilusKernel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderLifecycleTest {
    @Test
    void producesMultiplePartialFillsAndThenFilled() {
        try (NautilusKernel kernel = configuredKernel()) {
            kernel.exchange("XNAS").setMaxFillQuantity(4);
            kernel.start();
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(100, 100.0)});
            kernel.bus().publish(new OrderIntent("strategy", "AAPL", 1, 100, "corr", "order-1",
                    SignalDirection.BUY, 10, 100.0, 0, 0.0, TimeInForce.GTC, 0L));

            assertEquals(OrderStatus.PARTIALLY_FILLED, kernel.executionEngine().orderState("order-1").status());
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(101, 101.0)});
            assertEquals(OrderStatus.PARTIALLY_FILLED, kernel.executionEngine().orderState("order-1").status());
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(102, 102.0)});
            assertEquals(OrderStatus.FILLED, kernel.executionEngine().orderState("order-1").status());
            assertEquals(10, kernel.executionEngine().orderState("order-1").filledQuantity());
        }
    }

    @Test
    void supportsCancelModifyAndGtdExpiry() {
        try (NautilusKernel kernel = configuredKernel()) {
            kernel.start();
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(100, 100.0)});
            kernel.bus().publish(new LimitOrderIntent("strategy", "AAPL", 1, 100, "corr", "order-2",
                    SignalDirection.BUY, 10, 90.0, 0, 0.0, TimeInForce.GTD, 200L));
            kernel.bus().publish(new ModifyOrder("strategy", "AAPL", "order-2", "modify-1", 100, 10, 101.0));
            assertEquals(OrderStatus.ACCEPTED, kernel.executionEngine().orderState("order-2").status());

            kernel.bus().publish(new CancelOrder("strategy", "AAPL", "order-2", "cancel-1", 100));
            assertEquals(OrderStatus.CANCELED, kernel.executionEngine().orderState("order-2").status());

            kernel.bus().publish(new LimitOrderIntent("strategy", "AAPL", 1, 100, "corr", "order-3",
                    SignalDirection.BUY, 10, 90.0, 0, 0.0, TimeInForce.GTD, 200L));
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(200, 100.0)});
            assertEquals(OrderStatus.EXPIRED, kernel.executionEngine().orderState("order-3").status());
        }
    }

    @Test
    void cancelsUnfillableFokAndUnfilledIocRemainder() {
        try (NautilusKernel kernel = configuredKernel()) {
            kernel.exchange("XNAS").setMaxFillQuantity(4);
            kernel.start();
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(100, 100.0)});
            kernel.bus().publish(new OrderIntent("strategy", "AAPL", 1, 100, "corr", "order-4",
                    SignalDirection.BUY, 10, 100.0, 0, 0.0, TimeInForce.FOK, 0L));
            assertEquals(OrderStatus.CANCELED, kernel.executionEngine().orderState("order-4").status());

            kernel.bus().publish(new OrderIntent("strategy", "AAPL", 1, 100, "corr", "order-5",
                    SignalDirection.BUY, 10, 100.0, 0, 0.0, TimeInForce.IOC, 0L));
            OrderState iocState = kernel.executionEngine().orderState("order-5");
            assertEquals(OrderStatus.CANCELED, iocState.status());
            assertEquals(4, iocState.filledQuantity());
        }
    }

    @Test
    void rejectsCancelAfterFillAndModifyForMarketOrder() {
        try (NautilusKernel kernel = configuredKernel()) {
            kernel.start();
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(100, 100.0)});
            kernel.bus().publish(new OrderIntent("strategy", "AAPL", 1, 100, "corr", "order-6",
                    SignalDirection.BUY, 1, 100.0, 0, 0.0));
            kernel.bus().publish(new CancelOrder("strategy", "AAPL", "order-6", "cancel-6", 100));
            kernel.bus().publish(new ModifyOrder("strategy", "AAPL", "order-6", "modify-6", 100, 2, 101.0));
            assertEquals(OrderStatus.FILLED, kernel.executionEngine().orderState("order-6").status());
        }
    }

    @Test
    void triggersStopMarketWithoutIntermediateTriggeredState() {
        try (NautilusKernel kernel = configuredKernel()) {
            kernel.start();
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(100, 100.0)});
            kernel.bus().publish(new OrderIntent("strategy", "AAPL", 1, 100, "corr", "stop-market-1",
                    SignalDirection.BUY, 2, 105.0, 0, 0.0, TimeInForce.GTC, 0L,
                    105.0, TriggerType.LAST_PRICE));
            assertEquals(OrderStatus.ACCEPTED, kernel.executionEngine().orderState("stop-market-1").status());

            kernel.runBars(new com.abc.trading.data.Bar[] {bar(101, 105.0)});
            assertEquals(OrderStatus.FILLED, kernel.executionEngine().orderState("stop-market-1").status());
        }
    }

    @Test
    void triggersStopLimitThenWaitsForLimitMatch() {
        try (NautilusKernel kernel = configuredKernel()) {
            kernel.start();
            kernel.runBars(new com.abc.trading.data.Bar[] {bar(100, 100.0)});
            kernel.bus().publish(new LimitOrderIntent("strategy", "AAPL", 1, 100, "corr", "stop-limit-1",
                    SignalDirection.BUY, 2, 104.0, 0, 0.0, TimeInForce.GTC, 0L,
                    105.0, TriggerType.LAST_PRICE));

            kernel.runBars(new com.abc.trading.data.Bar[] {bar(101, 105.0)});
            assertEquals(OrderStatus.TRIGGERED, kernel.executionEngine().orderState("stop-limit-1").status());

            kernel.runBars(new com.abc.trading.data.Bar[] {bar(102, 103.0)});
            assertEquals(OrderStatus.FILLED, kernel.executionEngine().orderState("stop-limit-1").status());
        }
    }

    private static NautilusKernel configuredKernel() {
        NautilusKernel kernel = new NautilusKernel();
        kernel.addVenue("XNAS");
        kernel.addInstrument("AAPL", "XNAS");
        return kernel;
    }

    private static com.abc.trading.data.Bar bar(long timestamp, double close) {
        return new com.abc.trading.data.Bar("AAPL", timestamp, close, timestamp);
    }
}