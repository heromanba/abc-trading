package com.abc.trading.data;

import com.abc.trading.model.NanoTimestamp;
import com.abc.trading.model.identifiers.InstrumentId;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/** Exact European option contract metadata and intrinsic payoff semantics. */
public record OptionSpec(
        InstrumentId instrumentId,
        InstrumentId underlyingId,
        OptionRight right,
        Price strike,
        NanoTimestamp expiry,
        BigDecimal contractMultiplier,
        String settlementCurrency,
        BigDecimal marginRate) {
    private static final MathContext CONTEXT = MathContext.DECIMAL128;

    public OptionSpec {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(underlyingId, "underlyingId");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(strike, "strike");
        Objects.requireNonNull(expiry, "expiry");
        if (contractMultiplier == null || contractMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("contractMultiplier must be positive");
        }
        if (settlementCurrency == null || settlementCurrency.isBlank()) {
            throw new IllegalArgumentException("settlementCurrency is required");
        }
        if (marginRate == null || marginRate.signum() < 0) {
            throw new IllegalArgumentException("marginRate must be non-negative");
        }
    }

    public BigDecimal intrinsicValue(Price underlyingPrice) {
        BigDecimal difference = right == OptionRight.CALL
                ? underlyingPrice.asDecimal().subtract(strike.asDecimal())
                : strike.asDecimal().subtract(underlyingPrice.asDecimal());
        return difference.max(BigDecimal.ZERO).multiply(contractMultiplier, CONTEXT);
    }

    public BigDecimal payoff(Price underlyingPrice, Quantity quantity) {
        return intrinsicValue(underlyingPrice).multiply(quantity.asDecimal(), CONTEXT);
    }

    public BigDecimal marginRequirement(Price underlyingPrice, Quantity quantity) {
        return underlyingPrice.asDecimal().multiply(quantity.asDecimal(), CONTEXT)
                .multiply(contractMultiplier, CONTEXT).multiply(marginRate, CONTEXT);
    }
}
