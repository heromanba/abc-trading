package com.abc.trading.execution;

import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.system.NautilusKernel;
import com.abc.trading.trading.StrategyHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderEmulatorTest {
    @Test
    void releasesEmulatedOrderThroughRustShapedLifecycle() {
        try (NautilusKernel kernel = new NautilusKernel()) {
            AtomicReference<String> orderId = new AtomicReference<>();
            kernel.addVenue("XNAS");
            kernel.addInstrument("AAPL", "XNAS");
            kernel.addStrategy("AAPL", new StrategyHandler() {
                @Override
                public void onBar(Bar bar) {
                    if (orderId.get() == null) {
                        orderId.set(currentContext.emulatedMarket(
                                "AAPL", SignalDirection.BUY, 1, 105.0, TriggerType.LAST_PRICE));
                    }
                }

                private StrategyContextHolder currentContext = new StrategyContextHolder();

                @Override
                public void onBarWithContext(Bar bar, com.abc.trading.trading.StrategyContext context) {
                    currentContext.context = context;
                    StrategyHandler.super.onBarWithContext(bar, context);
                }
            });
            kernel.start();
            kernel.runBars(new Bar[] {new Bar("AAPL", 100, 100.0, 1)});

            String id = orderId.get();
            assertEquals(OrderStatus.EMULATED, kernel.executionEngine().orderState(id).status());
            assertTrue(kernel.executionEngine().orderEmulator().contains(id));

            kernel.runMarketData(new MarketDataSnapshot[] {
                    new MarketDataSnapshot("AAPL", 101, 104.0, 106.0, 105.0, 105.0, 105.0, 2)
            });

            assertEquals(OrderStatus.FILLED, kernel.executionEngine().orderState(id).status());
            assertFalse(kernel.executionEngine().orderEmulator().contains(id));
        }
    }

    private static final class StrategyContextHolder {
        private com.abc.trading.trading.StrategyContext context;

        private String emulatedMarket(String symbol, SignalDirection side, int quantity,
                double price, TriggerType triggerType) {
            return context.emulatedMarket(symbol, side, quantity, price, triggerType);
        }
    }
}