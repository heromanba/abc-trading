package com.abc.trading.backtest;

public record BacktestEngineConfig(
        boolean sortData,
        boolean streaming,
        boolean runAnalysis) {
    public static BacktestEngineConfig defaults() {
        return new BacktestEngineConfig(true, false, false);
    }
}
