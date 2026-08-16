package com.abc.trading.backtest;

public record BacktestResult(
        long iterations,
        long totalOrders,
        long totalFills,
        long startTimestamp,
        long endTimestamp) {
}
