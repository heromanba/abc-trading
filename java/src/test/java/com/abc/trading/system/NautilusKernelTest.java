package com.abc.trading.system;

import java.math.BigDecimal;
import com.abc.trading.data.Bar;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.MakerTakerFeeModel;
import com.abc.trading.execution.StaticLatencyModel;
import com.abc.trading.backtest.SimulatedVenueConfig;
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
            assertEquals(BigDecimal.valueOf(2), kernel.portfolio().position("AAPL"));
        }
    }

    @Test
    void kernelDeliversDelayedFillAndBooksTakerCommission() {
        List<com.abc.trading.execution.OrderFill> fills = new ArrayList<>();
        try (NautilusKernel kernel = new NautilusKernel()) {
            kernel.bus().subscribe(com.abc.trading.execution.OrderFill.class, fills::add, 100);
            kernel.addVenue(new SimulatedVenueConfig(
                    new com.abc.trading.execution.VenueId("XNAS"),
                    true,
                    true,
                    new StaticLatencyModel(10, 0, 0, 0),
                    new MakerTakerFeeModel(0.001, 0.01, "USD")));
            kernel.addInstrument("AAPL", "XNAS");
            kernel.start();
            kernel.runBars(new Bar[] {new Bar("AAPL", 100, 100.0, 1)});

            kernel.bus().publish(new OrderIntent(
                    "strategy", "AAPL", 1, 100, "corr", "order-latency",
                    SignalDirection.BUY, 2, 100.0, 0, 0.0));
            assertEquals(0, fills.size());

            kernel.runBars(new Bar[] {new Bar("AAPL", 109, 109.0, 2)});
            assertEquals(0, fills.size());
            kernel.runBars(new Bar[] {new Bar("AAPL", 110, 110.0, 3)});

            assertEquals(1, fills.size());
            assertEquals(110.0, fills.get(0).price());
            assertEquals(2.2, fills.get(0).commission().amount());
            assertEquals(BigDecimal.valueOf(2), kernel.portfolio().position("AAPL"));
            assertEquals(-2.2, kernel.portfolio().realizedPnl("AAPL"));
        }
    }
}
