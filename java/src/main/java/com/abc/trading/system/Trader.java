package com.abc.trading.system;

import com.abc.trading.data.Bar;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.trading.StrategyHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strategy lifecycle and data subscription owner. */
public final class Trader {
    private final MessageBus bus;
    private final Map<String, StrategyHandler> strategies = new LinkedHashMap<>();

    public Trader(MessageBus bus) {
        this.bus = bus;
    }

    public void registerStrategy(String symbol, StrategyHandler strategy) {
        if (strategies.putIfAbsent(symbol, strategy) != null) {
            throw new IllegalArgumentException("A strategy is already registered for: " + symbol);
        }
        bus.subscribe("data.bar." + symbol, Bar.class, strategy::onBar);
    }

    public void start() {
        strategies.values().forEach(StrategyHandler::onStart);
    }

    public void stop() {
        strategies.values().forEach(StrategyHandler::onStop);
    }
}