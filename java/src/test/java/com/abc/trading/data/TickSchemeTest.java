package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.model.orders.TrailingStopMarketOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickSchemeTest {
    @Test
    void usesInstrumentSpecificFixedTickForTicks() {
        TickScheme scheme = TickScheme.fixed(0.25);

        assertEquals(0.25, scheme.tickSize(100.0));
    }

    @Test
    void rejectsPriceTierUntilRustSupportsIt() {
        assertThrows(IllegalArgumentException.class, () -> new TrailingStopMarketOrder(
                "order-1",
                "strategy",
                "AAPL",
                SignalDirection.BUY,
                1,
                0.0,
                0.0,
                TriggerType.LAST_PRICE,
                1.0,
                TrailingOffsetType.PRICE_TIER,
                100L));
    }
}