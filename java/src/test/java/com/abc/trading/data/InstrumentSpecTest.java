package com.abc.trading.data;

import com.abc.trading.adapters.binance.BinanceInstrumentMetadata;
import com.abc.trading.adapters.binance.BinanceOrderValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstrumentSpecTest {
    @Test
    void validatesSizePrecisionAndIncrement() {
        InstrumentSpec instrument = new InstrumentSpec(
                "BTCUSDT", "BINANCE", TickScheme.fixed(0.1), "BTC", "USDT",
                0.05, 0.025, MarginModelType.NOTIONAL_RATE, 0.0, 0.0,
                3, new BigDecimal("0.001"));

        instrument.validateQuantity(Quantity.fromString("0.001", 3));
        assertThrows(IllegalArgumentException.class,
                () -> instrument.validateQuantity(Quantity.fromString("0.0015", 4)));
    }

    @Test
    void derivesInstrumentPrecisionFromBinanceStepSize() {
        BinanceInstrumentMetadata metadata = new BinanceInstrumentMetadata(
                "BTCUSDT", "BTC", "USDT", new BigDecimal("0.1"),
                new BigDecimal("0.001"), new BigDecimal("0.001"),
                new BigDecimal("0.05"), new BigDecimal("0.025"));

        assertEquals(3, metadata.sizePrecision());
        assertEquals(3, metadata.toInstrumentSpec("BINANCE").sizePrecision());
        BinanceOrderValidator.validate(metadata, new BigDecimal("0.001"), new BigDecimal("100.1"));
        assertThrows(IllegalArgumentException.class,
                () -> BinanceOrderValidator.validate(metadata, new BigDecimal("0.0015"), new BigDecimal("100.1")));
    }
}
