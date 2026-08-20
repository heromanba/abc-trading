package com.abc.trading.execution;

import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;

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

    @Override
    public boolean cancelOrder(CancelOrder command) {
        return exchange.cancelOrder(command);
    }

    @Override
    public boolean modifyOrder(ModifyOrder command) {
        return exchange.modifyOrder(command);
    }
}