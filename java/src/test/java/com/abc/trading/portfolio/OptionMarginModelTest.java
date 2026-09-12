package com.abc.trading.portfolio;

import com.abc.trading.data.OptionRight;
import com.abc.trading.data.OptionSpec;
import com.abc.trading.data.Price;
import com.abc.trading.data.Quantity;
import com.abc.trading.model.NanoTimestamp;
import com.abc.trading.model.identifiers.InstrumentId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionMarginModelTest {
    @Test
    void supportsCrossAndIsolatedLongShortOptionMargin() {
        OptionSpec option = new OptionSpec(new InstrumentId("AAPL-C.XNAS"), new InstrumentId("AAPL.XNAS"),
                OptionRight.PUT, Price.fromString("100", 0), new NanoTimestamp(1_000),
                BigDecimal.ONE, "USD", new BigDecimal("0.2"));
        Price spot = Price.fromString("90", 0);

        assertEquals(BigDecimal.ZERO, OptionMarginModel.initial(option, spot,
                BigDecimal.ONE, MarginMode.CROSS));
        assertTrue(OptionMarginModel.initial(option, spot, BigDecimal.ONE.negate(), MarginMode.ISOLATED)
                .compareTo(BigDecimal.ZERO) > 0);
        assertTrue(OptionMarginModel.maintenance(option, spot, BigDecimal.ONE.negate(), MarginMode.CROSS)
                .compareTo(BigDecimal.ZERO) > 0);
    }
}
