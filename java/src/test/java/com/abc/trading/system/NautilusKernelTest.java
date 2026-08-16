package com.abc.trading.system;

import com.abc.trading.data.Bar;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.trading.StrategyHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NautilusKernelTest {
    @Test
    void routesSortedBarsThroughTraderAndClock() {
        try (NautilusKernel kernel = new NautilusKernel()) {
            List<Long> timestamps = new ArrayList<>();
            kernel.addInstrument("AAPL", "XNAS");
            kernel.addStrategy("AAPL", new StrategyHandler() {
                @Override
                public void onBar(Bar bar) {
                    timestamps.add(bar.tsInit());
                }
            });
            kernel.start();
            kernel.runBars(new Bar[] {
                    new Bar("AAPL", 200, 20.0, 2),
                    new Bar("AAPL", 100, 10.0, 1),
            });

            assertEquals(List.of(100L, 200L), timestamps);
            assertEquals(200L, kernel.clock().timestampNs());
            assertEquals(2L, kernel.currentInputSequence());
        }
    }

    @Test
    void enforcesKernelLifecycleTransitions() {
        try (NautilusKernel kernel = new NautilusKernel()) {
            assertEquals(ComponentState.PRE_INITIALIZED, kernel.state());

            kernel.initialize();
            assertEquals(ComponentState.READY, kernel.state());
            kernel.start();
            assertEquals(ComponentState.RUNNING, kernel.state());
            kernel.stop();
            assertEquals(ComponentState.STOPPED, kernel.state());
            kernel.reset();
            assertEquals(ComponentState.READY, kernel.state());
            kernel.dispose();
            assertEquals(ComponentState.DISPOSED, kernel.state());
            assertThrows(IllegalStateException.class, kernel::start);
        }
    }

    @Test
    void sharesRustTransitionRulesForInvalidTriggers() {
        ComponentLifecycle lifecycle = new ComponentLifecycle();

        assertThrows(IllegalStateException.class, lifecycle::start);
        lifecycle.initialize();
        lifecycle.start();
        assertThrows(IllegalStateException.class, lifecycle::reset);
        lifecycle.startCompleted();
        lifecycle.stop();
        lifecycle.stopCompleted();
        lifecycle.dispose();
        lifecycle.disposeCompleted();
        assertEquals(ComponentState.DISPOSED, lifecycle.state());
    }

    @Test
    void simulatedVenueFillsMarketOrderAtCurrentBarPrice() {
        try (NautilusKernel kernel = new NautilusKernel()) {
            List<Double> fillPrices = new ArrayList<>();
            kernel.addVenue("XNAS");
            kernel.addInstrument("AAPL", "XNAS");
            kernel.bus().subscribe(OrderFill.class, fill -> fillPrices.add(fill.price()), 100);
            kernel.start();
            kernel.runBars(new Bar[] {new Bar("AAPL", 100, 123.45, 1)});

            kernel.bus().publish(new OrderIntent(
                    "strategy", "AAPL", 1, 100, "corr", "order-1",
                    SignalDirection.BUY, 2, 999.0, 2, 0.0));

            assertEquals(List.of(123.45), fillPrices);
            assertEquals(2, kernel.portfolio().position("AAPL"));
        }
    }
}
