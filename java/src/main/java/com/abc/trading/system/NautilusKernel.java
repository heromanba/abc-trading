package com.abc.trading.system;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.Bar;
import com.abc.trading.data.DataEngine;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.portfolio.Portfolio;
import com.abc.trading.risk.RiskEngine;
import com.abc.trading.execution.ExecutionEngine;
import com.abc.trading.execution.BacktestExecutionClient;
import com.abc.trading.execution.SimulatedExchange;
import com.abc.trading.execution.VenueId;
import com.abc.trading.execution.FeeModel;
import com.abc.trading.execution.LatencyModel;
import com.abc.trading.execution.MakerTakerFeeModel;
import com.abc.trading.execution.StaticLatencyModel;
import com.abc.trading.backtest.SimulatedVenueConfig;
import com.abc.trading.trading.StrategyHandler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final NautilusKernelConfig config;
    private final Map<VenueId, SimulatedExchange> exchanges = new LinkedHashMap<>();
    private final ComponentLifecycle lifecycle = new ComponentLifecycle();
    private long inputSequence;

    public NautilusKernel() {
        this(NautilusKernelConfig.defaults());
    }

    public NautilusKernel(NautilusKernelConfig config) {
        this.config = config;
        bus = new MessageBus(null);
        clock = new SimulatedClock();
        cache = new Cache();
        portfolio = new Portfolio(cache);
        riskEngine = new RiskEngine(Integer.MAX_VALUE, cache);
        executionEngine = new ExecutionEngine(bus, riskEngine, portfolio, cache);
        dataEngine = new DataEngine(bus);
        trader = new Trader(bus, cache);
    }

    public void addInstrument(String symbol, String venue) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue);
    }

    public void addVenue(String venue) {
        addVenue(SimulatedVenueConfig.defaults(new VenueId(venue)));
    }

    public void addVenue(SimulatedVenueConfig config) {
        VenueId venueId = config.venue();
        LatencyModel latencyModel = config.latencyModel() == null
                ? StaticLatencyModel.zero() : config.latencyModel();
        FeeModel feeModel = config.feeModel() == null
                ? MakerTakerFeeModel.zero() : config.feeModel();
        if (exchanges.containsKey(venueId)) {
            throw new IllegalArgumentException("Venue already registered: " + venueId.value());
        }
        SimulatedExchange exchange = new SimulatedExchange(venueId, bus::publish, latencyModel, feeModel);
        exchanges.put(venueId, exchange);
        executionEngine.registerClient(new BacktestExecutionClient(exchange));
        bus.subscribe("data.bar.*", Bar.class, exchange::processBar, 100);
    }

    public void addStrategy(String symbol, StrategyHandler strategy) {
        addStrategy(symbol, symbol, strategy);
    }

    public void addStrategy(String symbol, String strategyId, StrategyHandler strategy) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add strategies after initialization");
        }
        if (!cache.hasInstrument(symbol)) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        trader.registerStrategy(symbol, strategyId, strategy);
    }

    public void start() {
        if (lifecycle.state() == ComponentState.PRE_INITIALIZED) initialize();
        if (lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Kernel cannot start from state: " + lifecycle.state());
        }
        lifecycle.start();
        trader.start();
        lifecycle.startCompleted();
    }

    public void initialize() {
        lifecycle.initialize();
        trader.initialize();
    }

    public void runBars(Bar[] bars) {
        if (lifecycle.state() != ComponentState.RUNNING) {
            throw new IllegalStateException("Kernel must be running before processing bars");
        }
        if (bars == null) throw new IllegalArgumentException("bars are required");
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

    public ComponentState state() {
        return lifecycle.state();
    }

    public void stop() {
        if (lifecycle.state() != ComponentState.RUNNING) return;
        lifecycle.stop();
        trader.stop();
        lifecycle.stopCompleted();
    }

    public void reset() {
        if (lifecycle.state() != ComponentState.STOPPED) {
            throw new IllegalStateException("Kernel must be stopped before reset");
        }
        lifecycle.reset();
        inputSequence = 0;
        trader.reset();
        lifecycle.resetCompleted();
    }

    public void dispose() {
        if (lifecycle.state() == ComponentState.RUNNING) stop();
        if (lifecycle.state() != ComponentState.READY && lifecycle.state() != ComponentState.STOPPED) {
            throw new IllegalStateException("Kernel cannot dispose from state: " + lifecycle.state());
        }
        lifecycle.dispose();
        trader.dispose();
        lifecycle.disposeCompleted();
    }

    public MessageBus bus() { return bus; }
    public NautilusKernelConfig config() { return config; }
    public Clock clock() { return clock; }
    public Cache cache() { return cache; }
    public Portfolio portfolio() { return portfolio; }
    public RiskEngine riskEngine() { return riskEngine; }
    public ExecutionEngine executionEngine() { return executionEngine; }

    public SimulatedExchange exchange(String venue) {
        SimulatedExchange exchange = exchanges.get(new VenueId(venue));
        if (exchange == null) throw new IllegalArgumentException("Unknown venue: " + venue);
        return exchange;
    }

    @Override
    public void close() {
        if (lifecycle.state() != ComponentState.DISPOSED) dispose();
    }
}