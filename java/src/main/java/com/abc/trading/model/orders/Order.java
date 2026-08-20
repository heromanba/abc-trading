package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;

public sealed interface Order permits MarketOrder, LimitOrder, StopMarketOrder, StopLimitOrder {
    String clientOrderId();
    String strategyId();
    String symbol();
    SignalDirection side();
    int quantity();
    double price();
    long timestampNs();

    default double triggerPrice() { return 0.0; }

    default TriggerType triggerType() { return TriggerType.NO_TRIGGER; }
}
