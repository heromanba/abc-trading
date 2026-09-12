package com.abc.trading.data;

import com.abc.trading.model.NanoTimestamp;
import com.abc.trading.model.identifiers.InstrumentId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionGreeksTest {
    private static final OptionSpec CALL = new OptionSpec(
            new InstrumentId("AAPL-202701-C-100.XNAS"), new InstrumentId("AAPL.XNAS"),
            OptionRight.CALL, Price.fromString("100", 0), new NanoTimestamp(1_000_000_000),
            BigDecimal.ONE, "USD", new BigDecimal("0.15"));

    @Test
    void computesBlackScholesGreeks() {
        OptionGreeks greeks = CALL.greeks(Price.fromString("100", 0),
                new BigDecimal("0.20"), new BigDecimal("0.05"), new BigDecimal("1"));

        assertTrue(new BigDecimal("10.4506").subtract(greeks.theoreticalPrice())
                .abs().compareTo(new BigDecimal("0.01")) < 0);
        assertTrue(greeks.delta().compareTo(new BigDecimal("0.5")) > 0);
        assertTrue(greeks.gamma().signum() > 0);
        assertTrue(greeks.vega().signum() > 0);
    }

    @Test
    void settlesExpiryAndDistinguishesLongFromShortMargin() {
        assertEquals(new BigDecimal("10"), CALL.expirySettlement(
                Price.fromString("110", 0), Quantity.fromInt(1)));
        assertEquals(BigDecimal.ZERO, CALL.marginRequirement(
                Price.fromString("110", 0), Quantity.fromInt(1), false));
        assertTrue(CALL.marginRequirement(Price.fromString("110", 0), Quantity.fromInt(1), true).signum() > 0);
    }
}
