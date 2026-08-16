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
    public OrderFill submitMarketOrder(OrderIntent order) {
        return new OrderFill(
                order.strategyId(),
                order.symbol(),
                order.inputSequence(),
                order.marketTimestamp(),
                order.correlationId(),
                order.orderId(),
                order.side(),
                order.quantity(),
                exchange.currentPrice(order.symbol()),
                order.currentPosition(),
                order.realizedPnl());
    }
}