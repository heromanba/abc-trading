package com.abc.trading.system;

import com.abc.trading.data.Bar;
import com.abc.trading.trading.StrategyHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
