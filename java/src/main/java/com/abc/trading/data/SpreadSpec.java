package com.abc.trading.data;

import com.abc.trading.model.Decimal;
import com.abc.trading.model.identifiers.InstrumentId;
import com.abc.trading.execution.SignalDirection;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** Exact multi-leg spread contract with deterministic signed net value. */
public record SpreadSpec(InstrumentId instrumentId, List<SpreadLeg> legs, String settlementCurrency) {
    private static final MathContext CONTEXT = MathContext.DECIMAL128;

    public SpreadSpec {
        if (instrumentId == null) throw new IllegalArgumentException("instrumentId is required");
        if (legs == null || legs.size() < 2) throw new IllegalArgumentException("a spread needs at least two legs");
        if (settlementCurrency == null || settlementCurrency.isBlank()) {
            throw new IllegalArgumentException("settlementCurrency is required");
        }
        legs = List.copyOf(legs);
    }

    public Decimal netValue(List<Price> legPrices, Quantity quantity) {
        if (legPrices == null || legPrices.size() != legs.size()) {
            throw new IllegalArgumentException("legPrices must match legs");
        }
        BigDecimal value = BigDecimal.ZERO;
        for (int index = 0; index < legs.size(); index++) {
            SpreadLeg leg = legs.get(index);
            BigDecimal signed = leg.direction() == SignalDirection.BUY ? BigDecimal.ONE : BigDecimal.ONE.negate();
            value = value.add(legPrices.get(index).asDecimal()
                    .multiply(leg.ratio(), CONTEXT).multiply(signed, CONTEXT), CONTEXT);
        }
        return new Decimal(value.multiply(quantity.asDecimal(), CONTEXT));
    }
}
