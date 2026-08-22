package com.abc.trading.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickSchemeTest {
    @Test
    void usesInstrumentSpecificFixedTickForTicks() {
        TickScheme scheme = TickScheme.fixed(0.25);

        assertEquals(0.25, scheme.tickSize(100.0));
    }

}