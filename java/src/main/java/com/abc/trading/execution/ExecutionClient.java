package com.abc.trading.execution;

/** Shared execution contract for simulated and future live clients. */
public interface ExecutionClient {
    VenueId venue();

    void submitMarketOrder(OrderIntent order);

    void submitLimitOrder(LimitOrderIntent order);
}