package com.abc.trading.portfolio;

public record PositionUpdate(
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String orderId,
        int quantity,
        int position,
        double realizedPnl
) {
}