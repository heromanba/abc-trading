package com.abc.trading.system;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.Bar;
import com.abc.trading.data.DataEngine;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.portfolio.Portfolio;
import com.abc.trading.risk.RiskEngine;
import com.abc.trading.execution.ExecutionEngine;
import com.abc.trading.trading.StrategyHandler;

import java.util.Arrays;
import java.util.Comparator;

/** Minimal Java runtime composition root modeled after NautilusKernel. */
public final class NautilusKernel implements AutoCloseable {
    private final MessageBus bus;
    private final Clock clock;
    private final Cache cache;
    private final Portfolio portfolio;
    private final DataEngine dataEngine;
    private final RiskEngine riskEngine;
    private final ExecutionEngine executionEngine;
    private final Trader trader;
    private long inputSequence;
    private boolean started;

    public NautilusKernel() {
        bus = new MessageBus(null);
        clock = new SimulatedClock();
        cache = new Cache();
        portfolio = new Portfolio(cache);
        riskEngine = new RiskEngine(Integer.MAX_VALUE);
        executionEngine = new ExecutionEngine(bus, riskEngine, portfolio);
        dataEngine = new DataEngine(bus);
        trader = new Trader(bus);
    }

    public void addInstrument(String symbol, String venue) {
        if (started) throw new IllegalStateException("Cannot add instruments after start");
        cache.addInstrument(symbol, venue);
    }

    public void addStrategy(String symbol, StrategyHandler strategy) {
        if (started) throw new IllegalStateException("Cannot add strategies after start");
        if (!cache.hasInstrument(symbol)) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        trader.registerStrategy(symbol, strategy);
    }

    public void start() {
        started = true;
        trader.start();
    }

    public void runBars(Bar[] bars) {
        if (!started) throw new IllegalStateException("Kernel must be started before running");
        Arrays.sort(bars, Comparator.comparingLong(Bar::tsInit).thenComparing(Bar::symbol));
        for (Bar bar : bars) {
            inputSequence++;
            clock.setTimestampNs(bar.tsInit());
            dataEngine.publishBar(bar);
        }
    }

    public long currentInputSequence() {
        return inputSequence;
    }

    public MessageBus bus() { return bus; }
    public Clock clock() { return clock; }
    public Cache cache() { return cache; }
    public Portfolio portfolio() { return portfolio; }
    public RiskEngine riskEngine() { return riskEngine; }
    public ExecutionEngine executionEngine() { return executionEngine; }

    @Override
    public void close() {
        if (started) trader.stop();
        started = false;
    }
}