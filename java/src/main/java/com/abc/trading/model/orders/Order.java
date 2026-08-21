package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.execution.TrailingOffsetType;

public sealed interface Order permits MarketOrder, LimitOrder, StopMarketOrder, StopLimitOrder,
    TrailingStopMarketOrder, TrailingStopLimitOrder {
    String clientOrderId();
    String strategyId();
    String symbol();
    SignalDirection side();
    int quantity();
    double price();
    long timestampNs();

    default double triggerPrice() { return 0.0; }

    default TriggerType triggerType() { return TriggerType.NO_TRIGGER; }

    default double activationPrice() { return 0.0; }
    default double trailingOffset() { return 0.0; }
    default TrailingOffsetType trailingOffsetType() { return null; }
    default double limitOffset() { return 0.0; }

    default TriggerType emulationTrigger() { return TriggerType.NO_TRIGGER; }
}
