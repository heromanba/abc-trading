package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.OrderMatchingEngine;
import com.abc.trading.execution.SignalDirection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineSkeletonTest {
    @Test
    void iteratesChronologicalBarData() {
        BacktestDataIterator iterator = new BacktestDataIterator();
        iterator.addData(List.of(
                new Bar("AAPL", 200, 20.0, 2),
                new Bar("AAPL", 100, 10.0, 1)), true);

        assertEquals(100, iterator.next().tsInit());
        assertEquals(200, iterator.next().tsInit());
        assertEquals(2, iterator.size());
        assertThrows(IllegalStateException.class, iterator::next);
    }

    @Test
    void matchesMarketOrdersAndLeavesLimitMatchingExplicitlyPending() {
        OrderIntent order = new OrderIntent(
                "strategy", "AAPL", 1, 100, "correlation", "order",
                SignalDirection.BUY, 2, 999.0, 0, 0.0);
        OrderMatchingEngine matchingEngine = new OrderMatchingEngine();

        assertEquals(123.45, matchingEngine.matchMarketOrder(order, 123.45).price());
        assertThrows(
                UnsupportedOperationException.class,
                () -> matchingEngine.matchLimitOrder(order, 123.45));
    }

    @Test
    void exposesImplementedAndPendingRustMappedCapabilities() {
        assertEquals(true, EngineCapabilities.current().stream()
                .filter(capability -> capability.name().equals("market-order-matching"))
                .findFirst()
                .orElseThrow()
                .implemented());
        assertEquals(false, EngineCapabilities.current().stream()
                .filter(capability -> capability.name().equals("limit-order-matching"))
                .findFirst()
                .orElseThrow()
                .implemented());
    }
}
