package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.model.NanoTimestamp;
import com.abc.trading.model.identifiers.InstrumentId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DerivativeContractParityTest {
    @Test
    void calculatesExactOptionPayoffAndMargin() {
        OptionSpec option = new OptionSpec(
                new InstrumentId("AAPL-202701-C-100.XNAS"), new InstrumentId("AAPL.XNAS"),
                OptionRight.CALL, Price.fromString("100.00", 2), new NanoTimestamp(1_000_000),
                BigDecimal.ONE, "USD", new BigDecimal("0.15"));

        assertEquals(new BigDecimal("10.00"), option.payoff(Price.fromString("110.00", 2), Quantity.fromInt(1)));
        assertEquals(0, new BigDecimal("16.50").compareTo(option.marginRequirement(
                Price.fromString("110.00", 2), Quantity.fromInt(1))));
    }

    @Test
    void calculatesSignedSpreadNetValue() {
        SpreadSpec spread = new SpreadSpec(new InstrumentId("ES-CALENDAR.XCME"), List.of(
                new SpreadLeg(new InstrumentId("ES-MAR.XCME"), BigDecimal.ONE, SignalDirection.BUY),
                new SpreadLeg(new InstrumentId("ES-JUN.XCME"), BigDecimal.ONE, SignalDirection.SELL)), "USD");

        assertEquals(new BigDecimal("-5"), spread.netValue(List.of(
                Price.fromString("105", 0), Price.fromString("110", 0)), Quantity.fromInt(1)).asBigDecimal());
    }
}
