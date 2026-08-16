package com.abc.trading.execution;

/** Backtest execution client backed by a simulated exchange. */
public final class BacktestExecutionClient implements ExecutionClient {
    private final SimulatedExchange exchange;

    public BacktestExecutionClient(SimulatedExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public VenueId venue() {
        return exchange.venue();
    }

    @Override
    public void submitMarketOrder(OrderIntent order) {
        exchange.submitMarketOrder(order);
    }

    @Override
    public void submitLimitOrder(LimitOrderIntent order) {
        exchange.submitLimitOrder(order);
    }
}