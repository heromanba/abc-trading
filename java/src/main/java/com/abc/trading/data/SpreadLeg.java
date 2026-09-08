package com.abc.trading.data;

import com.abc.trading.model.identifiers.InstrumentId;
import com.abc.trading.execution.SignalDirection;

import java.math.BigDecimal;

/** One signed leg in a multi-leg spread contract. */
public record SpreadLeg(InstrumentId instrumentId, BigDecimal ratio, SignalDirection direction) {
    public SpreadLeg {
        if (instrumentId == null) throw new IllegalArgumentException("instrumentId is required");
        if (ratio == null || ratio.signum() <= 0) throw new IllegalArgumentException("ratio must be positive");
        if (direction == null || direction == SignalDirection.HOLD) {
            throw new IllegalArgumentException("direction must be BUY or SELL");
        }
    }
}
