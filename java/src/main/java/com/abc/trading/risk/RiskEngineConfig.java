package com.abc.trading.risk;

public record RiskEngineConfig(
        int maxQuantity,
        TradingState initialTradingState) {
    public RiskEngineConfig {
        if (maxQuantity <= 0) throw new IllegalArgumentException("maxQuantity must be positive");
        if (initialTradingState == null) throw new IllegalArgumentException("initialTradingState is required");
    }

    public static RiskEngineConfig defaults() {
        return new RiskEngineConfig(Integer.MAX_VALUE, TradingState.ACTIVE);
    }
}
