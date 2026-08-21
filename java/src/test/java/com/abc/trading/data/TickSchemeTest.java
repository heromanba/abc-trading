package com.abc.trading.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickSchemeTest {
    @Test
    void usesInstrumentSpecificFixedTickForTicks() {
        TickScheme scheme = TickScheme.fixed(0.25);

        assertEquals(0.25, scheme.tickSize(100.0));
    }

    @Test
    void selectsPriceTierAtBoundary() {
        TickScheme scheme = TickScheme.tiered(
                new PriceTier(0.0, 10.0, 0.01),
                new PriceTier(10.0, Double.POSITIVE_INFINITY, 0.25));

        assertEquals(0.01, scheme.tickSize(9.99));
        assertEquals(0.25, scheme.tickSize(10.0));
    }
}