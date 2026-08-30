package com.abc.trading.trading;

import com.abc.trading.data.Bar;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TimeInForce;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.cache.Cache;

import java.util.function.LongSupplier;

public final class StrategyContext {
    private final Cache cache;
    private final OrderApi orders;
    private final LongSupplier inputSequenceSupplier;
    private Bar currentBar;

    public StrategyContext(MessageBus bus, Cache cache, String strategyId) {
        this(bus, cache, strategyId, () -> 0L);
    }

    public StrategyContext(MessageBus bus, Cache cache, String strategyId, LongSupplier inputSequenceSupplier) {
        this.cache = cache;
        this.inputSequenceSupplier = inputSequenceSupplier;
        this.orders = new OrderApi(bus, strategyId, this);
    }

    public long inputSequence() {
        return inputSequenceSupplier.getAsLong();
    }

    public String limit(String symbol, SignalDirection side, int quantity, double limitPrice) {
        return orders.limit(symbol, side, quantity, limitPrice);
    }

    public String limit(String symbol, SignalDirection side, int quantity, double limitPrice,
            TimeInForce timeInForce, long expireTimeNs) {
        return orders.limit(symbol, side, quantity, limitPrice, timeInForce, expireTimeNs);
    }

    public String market(String symbol, SignalDirection side, int quantity, double price) {
        return orders.market(symbol, side, quantity, price);
    }

    public String market(String symbol, SignalDirection side, int quantity, double price,
            TimeInForce timeInForce, long expireTimeNs) {
        return orders.market(symbol, side, quantity, price, timeInForce, expireTimeNs);
    }

    public String emulatedMarket(String symbol, SignalDirection side, int quantity, double price,
            TriggerType emulationTrigger) {
        return orders.emulatedMarket(symbol, side, quantity, price, emulationTrigger);
    }

    public String emulatedLimit(String symbol, SignalDirection side, int quantity, double limitPrice,
            TriggerType emulationTrigger) {
        return orders.emulatedLimit(symbol, side, quantity, limitPrice, emulationTrigger);
    }

    public String stopMarket(String symbol, SignalDirection side, int quantity, double triggerPrice) {
        return orders.stopMarket(symbol, side, quantity, triggerPrice);
    }

    public String stopMarket(String symbol, SignalDirection side, int quantity, double triggerPrice,
            TimeInForce timeInForce, long expireTimeNs) {
        return orders.stopMarket(symbol, side, quantity, triggerPrice, timeInForce, expireTimeNs);
    }

    public String stopLimit(String symbol, SignalDirection side, int quantity, double limitPrice,
            double triggerPrice) {
        return orders.stopLimit(symbol, side, quantity, limitPrice, triggerPrice);
    }

    public String stopLimit(String symbol, SignalDirection side, int quantity, double limitPrice,
            double triggerPrice, TimeInForce timeInForce, long expireTimeNs) {
        return orders.stopLimit(symbol, side, quantity, limitPrice, triggerPrice, timeInForce, expireTimeNs);
    }

    public String trailingStopMarket(String symbol, SignalDirection side, int quantity,
            double activationPrice, double trailingOffset, TrailingOffsetType offsetType,
            TriggerType triggerType, TimeInForce timeInForce, long expireTimeNs) {
        return orders.trailingStopMarket(symbol, side, quantity, activationPrice, trailingOffset,
                offsetType, triggerType, timeInForce, expireTimeNs);
    }

    public String trailingStopLimit(String symbol, SignalDirection side, int quantity,
            double limitPrice, double activationPrice, double limitOffset, double trailingOffset,
            TrailingOffsetType offsetType, TriggerType triggerType, TimeInForce timeInForce,
            long expireTimeNs) {
        return orders.trailingStopLimit(symbol, side, quantity, limitPrice, activationPrice,
                limitOffset, trailingOffset, offsetType, triggerType, timeInForce, expireTimeNs);
    }

    public void cancel(String clientOrderId) { orders.cancel(clientOrderId); }

    public void modify(String clientOrderId, Integer quantity, Double price) {
        orders.modify(clientOrderId, quantity, price);
    }

    public void modify(String clientOrderId, Integer quantity, Double price, Double triggerPrice) {
        orders.modify(clientOrderId, quantity, price, triggerPrice);
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

    public java.math.BigDecimal position(String symbol) {
        return cache.position(symbol);
    }

    public long sequence() {
        return currentBar == null ? 0 : currentBar.sequence();
    }

}
