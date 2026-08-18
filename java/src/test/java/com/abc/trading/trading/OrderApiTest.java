package com.abc.trading.trading;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.Bar;
import com.abc.trading.execution.commands.OrderType;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.commands.SubmitOrder;
import com.abc.trading.msgbus.MessageBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderApiTest {
    @Test
    void createsTypedSubmitOrderCommandsForMarketAndLimitOrders() {
        MessageBus bus = new MessageBus(null);
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS");
        StrategyContext context = new StrategyContext(bus, cache, "momentum");
        List<SubmitOrder> commands = new ArrayList<>();
        bus.subscribe(SubmitOrder.class, commands::add);
        context.onBar(new Bar("AAPL", 100, 123.45, 7));

        context.orders().market("AAPL", SignalDirection.BUY, 10, 123.45);
        context.orders().limit("AAPL", SignalDirection.SELL, 5, 130.0);

        assertEquals(2, commands.size());
        assertEquals(OrderType.MARKET, commands.get(0).orderType());
        assertEquals(OrderType.LIMIT, commands.get(1).orderType());
        assertEquals("momentum", commands.get(0).strategyId());
        assertEquals("AAPL", commands.get(1).symbol());
    }
}
