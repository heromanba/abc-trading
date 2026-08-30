package com.abc.trading.portfolio;

import java.math.BigDecimal;
import com.abc.trading.data.Quantity;

public record PositionUpdate(
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String orderId,
        Quantity quantity,
        BigDecimal position,
        double realizedPnl
) {
        public PositionUpdate(String symbol, long inputSequence, long marketTimestamp, String orderId,
                        int quantity, int position, double realizedPnl) {
                this(symbol, inputSequence, marketTimestamp, orderId, Quantity.fromInt(quantity),
                                BigDecimal.valueOf(position), realizedPnl);
        }
}