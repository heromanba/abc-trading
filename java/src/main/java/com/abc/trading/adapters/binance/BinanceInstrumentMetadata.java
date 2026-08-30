package com.abc.trading.adapters.binance;

import com.abc.trading.data.InstrumentSpec;
import com.abc.trading.data.TickScheme;

import java.math.BigDecimal;

/** Binance exchangeInfo metadata needed to construct a Rust-shaped instrument. */
public record BinanceInstrumentMetadata(
        String symbol,
        String baseAsset,
        String quoteAsset,
        BigDecimal priceTickSize,
        BigDecimal quantityStepSize,
        BigDecimal minQuantity,
        BigDecimal initialMarginRate,
        BigDecimal maintenanceMarginRate) {
    public InstrumentSpec toInstrumentSpec(String venue) {
        return new InstrumentSpec(symbol, venue, TickScheme.fixed(priceTickSize.doubleValue()),
                baseAsset, quoteAsset, initialMarginRate.doubleValue(),
                maintenanceMarginRate.doubleValue(),
                com.abc.trading.data.MarginModelType.NOTIONAL_RATE, 0.0, 0.0,
                sizePrecision(), quantityStepSize);
    }

    public int sizePrecision() {
        return Math.max(0, quantityStepSize.stripTrailingZeros().scale());
    }
}
