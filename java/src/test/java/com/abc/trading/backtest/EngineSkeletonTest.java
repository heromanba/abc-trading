package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderMatchingEngine;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.SimulatedExchange;
import com.abc.trading.execution.VenueId;
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
        void matchesMarketOrdersAndLimitCrossingRules() {
        OrderIntent order = new OrderIntent(
                "strategy", "AAPL", 1, 100, "correlation", "order",
                SignalDirection.BUY, 2, 999.0, 0, 0.0);
        OrderMatchingEngine matchingEngine = new OrderMatchingEngine();

        assertEquals(123.45, matchingEngine.matchMarketOrder(order, 123.45).price());
        LimitOrderIntent limit = new LimitOrderIntent(
                "strategy", "AAPL", 1, 100, "correlation", "limit-order",
                SignalDirection.BUY, 2, 120.0, 0, 0.0);
        assertEquals(120.0, matchingEngine.matchLimitOrder(limit, 110.0).price());
        assertEquals(null, matchingEngine.matchLimitOrder(limit, 130.0));
    }

    @Test
    void queuesLimitOrdersUntilTheVenuePriceCrosses() {
        List<com.abc.trading.execution.OrderFill> fills = new java.util.ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        LimitOrderIntent limit = new LimitOrderIntent(
                "strategy", "AAPL", 1, 100, "correlation", "limit-order",
                SignalDirection.BUY, 2, 120.0, 0, 0.0);

        exchange.submitLimitOrder(limit);
        exchange.processBar(new com.abc.trading.data.Bar("AAPL", 100, 130.0, 1));
        assertEquals(0, fills.size());
        exchange.processBar(new com.abc.trading.data.Bar("AAPL", 200, 119.0, 2));

        assertEquals(1, fills.size());
        assertEquals(119.0, fills.get(0).price());
        assertEquals(0, exchange.pendingLimitOrderCount());
    }

    @Test
    void exposesImplementedAndPendingRustMappedCapabilities() {
        assertEquals(true, EngineCapabilities.current().stream()
                .filter(capability -> capability.name().equals("market-order-matching"))
                .findFirst()
                .orElseThrow()
                .implemented());
        assertEquals(true, EngineCapabilities.current().stream()
                .filter(capability -> capability.name().equals("limit-order-matching"))
                .findFirst()
                .orElseThrow()
                .implemented());
    }
}
