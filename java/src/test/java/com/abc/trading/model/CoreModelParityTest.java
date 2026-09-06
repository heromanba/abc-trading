package com.abc.trading.model;

import com.abc.trading.data.DerivativeType;
import com.abc.trading.data.InstrumentSpec;
import com.abc.trading.data.InstrumentType;
import com.abc.trading.data.Price;
import com.abc.trading.data.TickScheme;
import com.abc.trading.model.identifiers.ClientOrderId;
import com.abc.trading.model.identifiers.InstrumentId;
import com.abc.trading.model.identifiers.PositionId;
import com.abc.trading.model.identifiers.StrategyId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreModelParityTest {
    @Test
    void preservesExactDecimalPriceMoneyAndTimestampSemantics() {
        Decimal decimal = new Decimal("1234567890.123456789");
        Price price = Price.fromString("100.25", 2);
        Money money = new Money(decimal, "USD");

        assertEquals(new BigDecimal("1234567890.123456789"), decimal.asBigDecimal());
        assertEquals(new BigDecimal("100.25"), price.asDecimal());
        assertEquals(decimal, money.decimal());
        assertEquals(new NanoTimestamp(100), new NanoTimestamp(99).plus(1));
        assertThrows(IllegalArgumentException.class, () -> new NanoTimestamp(-1));
    }

    @Test
    void exposesTypedRustStyleIdentifiers() {
        assertEquals("BTCUSDT.BINANCE", new InstrumentId("BTCUSDT.BINANCE").toString());
        assertEquals("order-1", new ClientOrderId("order-1").toString());
        assertEquals("position-1", new PositionId("position-1").toString());
        assertEquals("strategy-1", new StrategyId("strategy-1").toString());
        assertThrows(IllegalArgumentException.class, () -> new StrategyId(" "));
    }

    @Test
    void classifiesExistingInstrumentSpecsByNautilusInstrumentTaxonomy() {
        InstrumentSpec spot = InstrumentSpec.defaults("EURUSD", "FX", TickScheme.fixed(0.00001));
        InstrumentSpec future = new InstrumentSpec("ES", "XCME", TickScheme.fixed(0.25),
                "USD", "USD", 0.1, 0.05, com.abc.trading.data.MarginModelType.NOTIONAL_RATE,
                0, 0, 0, BigDecimal.ONE, 2, new BigDecimal("0.25"),
                DerivativeType.LINEAR_FUTURE, BigDecimal.ONE, "USD");

        assertEquals(InstrumentType.FX, spot.instrumentType());
        assertEquals(InstrumentType.FUTURE, future.instrumentType());
        future.validatePrice(Price.fromString("5000.00", 2));
    }
}
