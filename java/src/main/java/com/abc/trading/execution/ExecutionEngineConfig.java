package com.abc.trading.execution;

public record ExecutionEngineConfig(
        boolean allowMarketOrders,
        boolean allowLimitOrders) {
    public static ExecutionEngineConfig defaults() {
        return new ExecutionEngineConfig(true, false);
    }
}
