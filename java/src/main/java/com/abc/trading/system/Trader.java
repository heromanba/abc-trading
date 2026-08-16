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
    private final ComponentLifecycle lifecycle = new ComponentLifecycle();

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
        if (lifecycle.state() != ComponentState.READY && lifecycle.state() != ComponentState.STOPPED) {
            throw new IllegalStateException("Invalid trader state: " + lifecycle.state());
        }
        lifecycle.start();
        strategies.values().forEach(StrategyHandler::onStart);
        lifecycle.startCompleted();
    }

    public void stop() {
        if (lifecycle.state() != ComponentState.RUNNING) return;
        lifecycle.stop();
        strategies.values().forEach(StrategyHandler::onStop);
        lifecycle.stopCompleted();
    }

    public void initialize() {
        lifecycle.initialize();
    }

    public void reset() {
        lifecycle.reset();
        lifecycle.resetCompleted();
    }

    public void dispose() {
        if (lifecycle.state() == ComponentState.RUNNING) stop();
        lifecycle.dispose();
        lifecycle.disposeCompleted();
    }

    public ComponentState state() {
        return lifecycle.state();
    }
}