package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;

public sealed interface Order permits MarketOrder, LimitOrder {
    String clientOrderId();
    String strategyId();
    String symbol();
    SignalDirection side();
    int quantity();
    double price();
    long timestampNs();
}
