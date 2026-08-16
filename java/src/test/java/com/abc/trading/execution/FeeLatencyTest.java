package com.abc.trading.execution;

import com.abc.trading.data.Bar;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeLatencyTest {
    @Test
    void staticLatencyAddsBaseToEachOperation() {
        StaticLatencyModel latency = new StaticLatencyModel(1_000_000, 2_000_000, 3_000_000, 4_000_000);

        assertEquals(3_000_000, latency.getInsertLatencyNs());
        assertEquals(4_000_000, latency.getUpdateLatencyNs());
        assertEquals(5_000_000, latency.getDeleteLatencyNs());
    }

    @Test
    void marketOrderIsDeferredUntilInsertLatencyIsDue() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(
                new VenueId("XNAS"),
                fills::add,
                new StaticLatencyModel(10, 0, 0, 0),
                MakerTakerFeeModel.zero());
        exchange.processBar(new Bar("AAPL", 100, 100.0, 1));
        OrderIntent order = new OrderIntent(
                "strategy", "AAPL", 1, 100, "correlation", "order",
                SignalDirection.BUY, 1, 100.0, 0, 0.0);

        exchange.submitMarketOrder(order);
        assertEquals(0, fills.size());
        exchange.processBar(new Bar("AAPL", 109, 101.0, 2));
        assertEquals(0, fills.size());
        exchange.processBar(new Bar("AAPL", 110, 102.0, 3));

        assertEquals(1, fills.size());
        assertEquals(102.0, fills.get(0).price());
    }

    @Test
    void makerTakerFeeUsesNotionalAndLiquiditySide() {
        MakerTakerFeeModel fee = new MakerTakerFeeModel(0.001, 0.002, "USD");

        assertEquals(1.0, fee.calculate(1, 1000.0, LiquiditySide.MAKER).amount());
        assertEquals(2.0, fee.calculate(1, 1000.0, LiquiditySide.TAKER).amount());
    }

    @Test
    void fixedFeeCanBeChargedOnlyOnce() {
        FixedFeeModel fee = new FixedFeeModel(new Commission(2.5, "USD"), true);

        assertEquals(2.5, fee.calculate(1, 100.0, LiquiditySide.TAKER).amount());
        assertEquals(0.0, fee.calculate(1, 100.0, LiquiditySide.TAKER).amount());
    }
}
