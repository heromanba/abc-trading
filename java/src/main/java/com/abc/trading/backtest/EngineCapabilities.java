package com.abc.trading.backtest;

import java.util.List;

public final class EngineCapabilities {
    private EngineCapabilities() {
    }

    public static List<EngineCapability> current() {
        return List.of(
                new EngineCapability("market-order-matching", true, "crates/execution/src/matching_engine/engine.rs", "Bar-close market fills"),
                new EngineCapability("limit-order-matching", true, "crates/execution/src/matching_engine/engine.rs", "Close-price crossing with pending orders"),
                new EngineCapability("simulated-exchange", true, "crates/backtest/src/exchange.rs", "Single-price state"),
                new EngineCapability("backtest-data-iterator", true, "crates/backtest/src/data_iterator.rs", "Bar-only iterator"),
                new EngineCapability("latency-model", false, "crates/backtest/src/models/latency.rs", "Pending"),
                new EngineCapability("fee-model", false, "crates/backtest/src/models/fee.rs", "Pending"),
                new EngineCapability("accounting", false, "crates/portfolio/src/portfolio.rs", "Minimal position and realized PnL"),
                new EngineCapability("actors", true, "crates/common/src/actor/mod.rs", "Lifecycle interface only"),
                new EngineCapability("execution-algorithms", true, "crates/trading/src/algorithm/mod.rs", "Interface only")
        );
    }
}
