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
                new EngineCapability("latency-model", true, "crates/execution/src/models/latency.rs", "Static operation latency with FIFO timestamp ordering"),
                new EngineCapability("fee-model", true, "crates/execution/src/models/fee.rs", "Fixed, maker/taker, per-contract, probability, capped, and notional models"),
                new EngineCapability("accounting", true, "crates/portfolio/src/portfolio.rs", "Single-currency and configured multi-currency balance, margin, commission, and PnL ledger"),
                new EngineCapability("actors", true, "crates/common/src/actor/mod.rs", "Lifecycle interface only"),
                new EngineCapability("execution-algorithms", true, "crates/trading/src/algorithm/mod.rs", "Interface only")
        );
    }
}
