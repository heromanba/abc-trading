package com.abc.trading.execution;

import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;

/** Shared execution contract for simulated and future live clients. */
public interface ExecutionClient {
    VenueId venue();

    void submitMarketOrder(OrderIntent order);

    void submitLimitOrder(LimitOrderIntent order);

    boolean cancelOrder(CancelOrder command);

    int cancelAllOrders(String symbol, long timestampNs);

    boolean modifyOrder(ModifyOrder command);

    void executeLiquidation(OrderIntent order);
}