package com.abc.trading.trading;

import com.abc.trading.data.Bar;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.cache.Cache;

public final class StrategyContext {
    private final Cache cache;
    private final OrderApi orders;
    private Bar currentBar;

    public StrategyContext(MessageBus bus, Cache cache, String strategyId) {
        this.cache = cache;
        this.orders = new OrderApi(bus, strategyId, this);
    }

    public long inputSequence() {
        return currentBar == null ? 0 : currentBar.sequence();
    }

    public void limit(String symbol, SignalDirection side, int quantity, double limitPrice) {
        orders.limit(symbol, side, quantity, limitPrice);
    }

    public void market(String symbol, SignalDirection side, int quantity, double price) {
        orders.market(symbol, side, quantity, price);
    }

    public long marketTimestamp() {
        if (currentBar == null) throw new IllegalStateException("No current bar");
        return currentBar.tsInit();
    }

    public void onBar(Bar bar) {
        currentBar = bar;
    }

    public OrderApi orders() {
        return orders;
    }

    public int position(String symbol) {
        return cache.position(symbol);
    }

    public long sequence() {
        return currentBar == null ? 0 : currentBar.sequence();
    }

}
