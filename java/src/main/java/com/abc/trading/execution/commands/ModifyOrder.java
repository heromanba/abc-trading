package com.abc.trading.execution.commands;

public record ModifyOrder(
        String strategyId,
        String symbol,
        String clientOrderId,
        String commandId,
        long timestampNs,
        Integer quantity,
        Double price) {
    public ModifyOrder {
        if (strategyId == null || strategyId.isBlank()) throw new IllegalArgumentException("strategyId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (quantity == null && price == null) throw new IllegalArgumentException("quantity or price is required");
        if (quantity != null && quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (price != null && (!Double.isFinite(price) || price <= 0.0)) {
            throw new IllegalArgumentException("price must be finite and positive");
        }
    }
}