package com.abc.trading.portfolio;

public record PortfolioConfig(
        boolean calculatePnl,
        boolean enableSnapshots) {
    public static PortfolioConfig defaults() {
        return new PortfolioConfig(true, true);
    }
}
