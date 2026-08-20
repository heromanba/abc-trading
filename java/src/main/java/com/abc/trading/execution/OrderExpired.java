package com.abc.trading.execution;

public record OrderExpired(
        String strategyId,
        String symbol,
        String orderId,
        SignalDirection side,
        int remainingQuantity,
        double price,
        long marketTimestamp) { }