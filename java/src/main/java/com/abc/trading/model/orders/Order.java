package com.abc.trading.model.orders;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

import java.math.BigDecimal;

public sealed interface Order permits MarketOrder, LimitOrder, StopMarketOrder, StopLimitOrder,
    TrailingStopMarketOrder, TrailingStopLimitOrder {
    String clientOrderId();
    String strategyId();
    String symbol();
    SignalDirection side();
    Quantity quantity();
    Price price();
    default BigDecimal priceDecimal() { return price().asDecimal(); }
    default Price priceValue(int precision) { return price(); }
    long timestampNs();

    default Price triggerPrice() { return Price.fromDecimal(BigDecimal.ZERO, 0); }
    default BigDecimal triggerPriceDecimal() { return triggerPrice().asDecimal(); }

    default TriggerType triggerType() { return TriggerType.NO_TRIGGER; }

    default Price activationPrice() { return Price.fromDecimal(BigDecimal.ZERO, 0); }
    default double trailingOffset() { return 0.0; }
    default TrailingOffsetType trailingOffsetType() { return null; }
    default double limitOffset() { return 0.0; }

    default TriggerType emulationTrigger() { return TriggerType.NO_TRIGGER; }
}
