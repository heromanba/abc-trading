package com.abc.trading.execution.commands;

public record CancelOrder(
        String strategyId,
        String symbol,
        String clientOrderId,
        String commandId,
        long timestampNs) {
    public CancelOrder {
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
    }
}