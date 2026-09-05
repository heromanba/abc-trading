package com.abc.trading.system;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.TickScheme;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.data.OrderBookDelta;
import com.abc.trading.data.OrderBookL3Snapshot;
import com.abc.trading.data.OrderBookL3Delta;
import com.abc.trading.data.TradeTick;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.data.FundingRateUpdate;
import com.abc.trading.data.DerivativeType;
import com.abc.trading.portfolio.AccountType;
import com.abc.trading.data.MarginModelType;
import java.math.BigDecimal;
import com.abc.trading.data.DataClient;
import com.abc.trading.adapters.binance.BinanceFuturesConfig;
import com.abc.trading.adapters.binance.BinanceFuturesLiveRuntime;
import com.abc.trading.adapters.binance.BinanceHttpTransport;
import com.abc.trading.data.DataEngine;
import com.abc.trading.msgbus.JacksonSerializer;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.msgbus.MessageBusBacking;
import com.abc.trading.msgbus.DisruptorMarketDataIngress;
import com.abc.trading.msgbus.RedisMessageBusBacking;
import com.abc.trading.portfolio.Portfolio;
import com.abc.trading.portfolio.AccountMarginCall;
import com.abc.trading.portfolio.AccountLiquidationRequired;
import com.abc.trading.portfolio.AccountState;
import com.abc.trading.portfolio.AccountStateEvent;
import com.abc.trading.portfolio.FundingPayment;
import com.abc.trading.portfolio.MarginMode;
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
import java.util.ArrayList;
import java.util.List;

/** Minimal Java runtime composition root modeled after NautilusKernel. */
public final class NautilusKernel implements AutoCloseable {
    private final MessageBus bus;
    private final DisruptorMarketDataIngress marketDataIngress;
    private final Clock clock;
    private final Cache cache;
    private final Portfolio portfolio;
    private final DataEngine dataEngine;
    private final RiskEngine riskEngine;
    private final ExecutionEngine executionEngine;
    private final Trader trader;
    private final NautilusKernelConfig config;
    private final MessageBusBacking externalBacking;
    private RedisMessageBusBacking.RedisSubscription externalSubscription;
    private final Map<VenueId, SimulatedExchange> exchanges = new LinkedHashMap<>();
    private final List<DataClient> liveClients = new ArrayList<>();
    private final ComponentLifecycle lifecycle = new ComponentLifecycle();
    private long inputSequence;

    public NautilusKernel() {
        this(NautilusKernelConfig.defaults(), null);
    }

    public NautilusKernel(NautilusKernelConfig config) {
        this(config, null);
    }

    public NautilusKernel(MessageBusBacking externalBacking) {
        this(NautilusKernelConfig.defaults(), externalBacking);
    }

    public NautilusKernel(NautilusKernelConfig config, MessageBusBacking externalBacking) {
        this.config = config;
        this.externalBacking = externalBacking;
        bus = new MessageBus(externalBacking == null ? null : new JacksonSerializer(), externalBacking);
        clock = new SimulatedClock();
        cache = new Cache();
        portfolio = new Portfolio(cache);
        riskEngine = new RiskEngine(Integer.MAX_VALUE, cache, portfolio);
        executionEngine = new ExecutionEngine(bus, riskEngine, portfolio, cache);
        dataEngine = new DataEngine(bus);
        bus.subscribe(FundingRateUpdate.class, update -> {
            if (cache.hasInstrument(update.symbol())) {
                FundingPayment payment = portfolio.applyFunding(update);
                if (payment != null) {
                    bus.publish(payment);
                    AccountState state = portfolio.accountState(
                            cache.venue(update.symbol()), update.tsEvent());
                    if (state != null) {
                        bus.publish(new AccountStateEvent(state));
                        if (state.marginCall()) bus.publish(new AccountMarginCall(state));
                        if (state.liquidationRequired()) {
                            bus.publish(new AccountLiquidationRequired(state));
                        }
                    }
                }
            }
        }, 100);
        marketDataIngress = new DisruptorMarketDataIngress(this::publishLiveMarketData);
        trader = new Trader(bus, cache, () -> inputSequence);
    }

    public void addInstrument(String symbol, String venue) {
        addInstrument(symbol, venue, TickScheme.fixed(0.01));
    }

    public void addInstrument(String symbol, String venue, double tickSize) {
        addInstrument(symbol, venue, TickScheme.fixed(tickSize));
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue, tickScheme);
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
                marginInitialRate, marginMaintenanceRate);
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
                marginInitialRate, marginMaintenanceRate, marginModelType,
                initialMarginPerUnit, maintenanceMarginPerUnit);
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit,
            int sizePrecision, BigDecimal sizeIncrement) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
                marginInitialRate, marginMaintenanceRate, marginModelType,
                initialMarginPerUnit, maintenanceMarginPerUnit, sizePrecision, sizeIncrement);
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit,
            int sizePrecision, BigDecimal sizeIncrement, int pricePrecision,
            BigDecimal priceTickSize) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
                marginInitialRate, marginMaintenanceRate, marginModelType,
                initialMarginPerUnit, maintenanceMarginPerUnit, sizePrecision,
                sizeIncrement, pricePrecision, priceTickSize);
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit,
            int sizePrecision, BigDecimal sizeIncrement, int pricePrecision,
            BigDecimal priceTickSize, DerivativeType derivativeType,
            BigDecimal contractMultiplier, String settlementCurrency) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add instruments after initialization");
        }
        cache.addInstrument(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
            marginInitialRate, marginMaintenanceRate, marginModelType,
            initialMarginPerUnit, maintenanceMarginPerUnit, sizePrecision, sizeIncrement,
            pricePrecision, priceTickSize, derivativeType, contractMultiplier, settlementCurrency);
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
        SimulatedExchange exchange = new SimulatedExchange(venueId, bus::publish, bus::publish,
            latencyModel, feeModel, cache::tickSize);
        exchanges.put(venueId, exchange);
        executionEngine.registerClient(new BacktestExecutionClient(exchange));
        bus.subscribe("data.bar.*", Bar.class, exchange::processBar, 100);
        bus.subscribe("data.market.*", MarketDataSnapshot.class, exchange::processMarketData, 100);
        bus.subscribe("data.book.*", OrderBookSnapshot.class, exchange::processOrderBook, 100);
        bus.subscribe("data.book.delta.*", OrderBookDelta.class, exchange::processOrderBookDelta, 100);
        bus.subscribe("data.book.l3.*", OrderBookL3Snapshot.class, exchange::processOrderBookL3, 100);
        bus.subscribe("data.book.l3.delta.*", OrderBookL3Delta.class, exchange::processOrderBookL3Delta, 100);
        bus.subscribe("data.trade.*", TradeTick.class, exchange::processTradeTick, 100);
    }

    public BinanceFuturesLiveRuntime addBinanceFutures(BinanceFuturesConfig config,
            BinanceHttpTransport http) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot add live adapters after initialization");
        }
        BinanceFuturesLiveRuntime runtime = new BinanceFuturesLiveRuntime(
            config, http, bus::publish, marketDataIngress::publish);
        executionEngine.registerClient(runtime);
        liveClients.add(runtime);
        return runtime;
    }

    public BinanceFuturesLiveRuntime addBinanceFutures(BinanceFuturesConfig config) {
        return addBinanceFutures(config,
                new com.abc.trading.adapters.binance.JavaBinanceHttpTransport(
                        config.httpBaseUrl(), config.requestTimeout()));
    }

    public void configureAccount(String venue, double startingBalance, String currency, double leverage) {
        configureAccount(venue, startingBalance, currency, leverage, AccountType.MARGIN);
    }

    public void configureAccount(String venue, double startingBalance, String currency, double leverage,
            AccountType accountType) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot configure accounts after initialization");
        }
        portfolio.configureAccount(venue, startingBalance, currency, leverage, accountType);
    }

    public void configureAccount(String venue, BigDecimal startingBalance, String currency,
            BigDecimal leverage, AccountType accountType) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot configure accounts after initialization");
        }
        portfolio.configureAccount(venue, startingBalance, currency, leverage, accountType);
    }

    public void configureAccount(String venue, BigDecimal startingBalance, String currency,
            BigDecimal leverage, AccountType accountType, MarginMode marginMode) {
        if (lifecycle.state() != ComponentState.PRE_INITIALIZED && lifecycle.state() != ComponentState.READY) {
            throw new IllegalStateException("Cannot configure accounts after initialization");
        }
        portfolio.configureAccount(venue, startingBalance, currency, leverage, accountType, marginMode);
    }

    public void deposit(String venue, String currency, double amount) {
        portfolio.deposit(venue, currency, amount);
    }

    public void deposit(String venue, String currency, BigDecimal amount) {
        portfolio.deposit(venue, currency, amount);
    }

    public void setFxRate(String fromCurrency, String toCurrency, double rate) {
        portfolio.setFxRate(fromCurrency, toCurrency, rate);
    }

    public void setFxRate(String fromCurrency, String toCurrency, BigDecimal rate) {
        portfolio.setFxRate(fromCurrency, toCurrency, rate);
    }

    public com.abc.trading.portfolio.AccountState accountState(String venue, long timestamp) {
        return portfolio.accountState(venue, timestamp);
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
        for (DataClient client : liveClients) client.start();
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

    public void runMarketData(MarketDataSnapshot[] snapshots) {
        if (lifecycle.state() != ComponentState.RUNNING) {
            throw new IllegalStateException("Kernel must be running before processing market data");
        }
        if (snapshots == null) throw new IllegalArgumentException("snapshots are required");
        Arrays.sort(snapshots, Comparator.comparingLong(MarketDataSnapshot::tsInit)
                .thenComparing(MarketDataSnapshot::symbol));
        for (MarketDataSnapshot snapshot : snapshots) {
            inputSequence++;
            clock.setTimestampNs(snapshot.tsInit());
            dataEngine.publishMarketData(snapshot);
        }
    }

    public void runOrderBooks(OrderBookSnapshot[] snapshots) {
        if (lifecycle.state() != ComponentState.RUNNING) {
            throw new IllegalStateException("Kernel must be running before processing order books");
        }
        if (snapshots == null) throw new IllegalArgumentException("snapshots are required");
        Arrays.sort(snapshots, Comparator.comparingLong(OrderBookSnapshot::tsInit)
                .thenComparing(OrderBookSnapshot::symbol));
        for (OrderBookSnapshot snapshot : snapshots) {
            inputSequence++;
            clock.setTimestampNs(snapshot.tsInit());
            dataEngine.publishOrderBook(snapshot);
        }
    }

    public void runOrderBookDeltas(OrderBookDelta[] deltas) {
        if (lifecycle.state() != ComponentState.RUNNING) {
            throw new IllegalStateException("Kernel must be running before processing order-book deltas");
        }
        if (deltas == null) throw new IllegalArgumentException("deltas are required");
        Arrays.sort(deltas, Comparator.comparingLong(OrderBookDelta::tsInit)
                .thenComparing(OrderBookDelta::symbol));
        for (OrderBookDelta delta : deltas) {
            inputSequence++;
            clock.setTimestampNs(delta.tsInit());
            dataEngine.publishOrderBookDelta(delta);
        }
    }

    public void runOrderBooksL3(OrderBookL3Snapshot[] snapshots) {
        if (lifecycle.state() != ComponentState.RUNNING) throw new IllegalStateException("Kernel must be running before processing L3 books");
        if (snapshots == null) throw new IllegalArgumentException("snapshots are required");
        Arrays.sort(snapshots, Comparator.comparingLong(OrderBookL3Snapshot::tsInit).thenComparing(OrderBookL3Snapshot::symbol));
        for (OrderBookL3Snapshot snapshot : snapshots) {
            inputSequence++;
            clock.setTimestampNs(snapshot.tsInit());
            dataEngine.publishOrderBookL3(snapshot);
        }
    }

    public void runOrderBookL3Deltas(OrderBookL3Delta[] deltas) {
        if (lifecycle.state() != ComponentState.RUNNING) throw new IllegalStateException("Kernel must be running before processing L3 deltas");
        if (deltas == null) throw new IllegalArgumentException("deltas are required");
        Arrays.sort(deltas, Comparator.comparingLong(OrderBookL3Delta::tsInit).thenComparing(OrderBookL3Delta::symbol));
        for (OrderBookL3Delta delta : deltas) {
            inputSequence++;
            clock.setTimestampNs(delta.tsInit());
            dataEngine.publishOrderBookL3Delta(delta);
        }
    }

    public void runTradeTicks(TradeTick[] trades) {
        if (lifecycle.state() != ComponentState.RUNNING) throw new IllegalStateException("Kernel must be running before processing trades");
        if (trades == null) throw new IllegalArgumentException("trades are required");
        Arrays.sort(trades, Comparator.comparingLong(TradeTick::tsInit).thenComparing(TradeTick::symbol));
        for (TradeTick trade : trades) {
            inputSequence++;
            clock.setTimestampNs(trade.tsInit());
            dataEngine.publishTradeTick(trade);
        }
    }

    public void runFxRates(FxRateUpdate[] updates) {
        if (lifecycle.state() != ComponentState.RUNNING) throw new IllegalStateException("Kernel must be running before processing FX rates");
        if (updates == null) throw new IllegalArgumentException("updates are required");
        Arrays.sort(updates, Comparator.comparingLong(FxRateUpdate::tsInit)
                .thenComparing(FxRateUpdate::fromCurrency).thenComparing(FxRateUpdate::toCurrency));
        for (FxRateUpdate update : updates) {
            inputSequence++;
            clock.setTimestampNs(update.tsInit());
            dataEngine.publishFxRate(update);
        }
    }

    public void runFundingRates(FundingRateUpdate[] updates) {
        if (lifecycle.state() != ComponentState.RUNNING) throw new IllegalStateException("Kernel must be running before processing funding rates");
        if (updates == null) throw new IllegalArgumentException("updates are required");
        Arrays.sort(updates, Comparator.comparingLong(FundingRateUpdate::tsInit)
                .thenComparing(FundingRateUpdate::symbol));
        for (FundingRateUpdate update : updates) {
            inputSequence++;
            clock.setTimestampNs(update.tsInit());
            dataEngine.publishFundingRate(update);
        }
    }

    public ComponentState state() {
        return lifecycle.state();
    }

    public void stop() {
        if (lifecycle.state() != ComponentState.RUNNING) return;
        lifecycle.stop();
        for (DataClient client : liveClients) client.stop();
        marketDataIngress.drain();
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
        if (externalSubscription != null) {
            externalSubscription.close();
            externalSubscription = null;
        }
        if (externalBacking != null) externalBacking.close();
        marketDataIngress.close();
        trader.dispose();
        lifecycle.disposeCompleted();
    }

    public MessageBus bus() { return bus; }
    public <T> void registerExternalType(Class<T> cls) { bus.registerExternalType(cls); }
    public <T> void publishExternal(String topic, T message) { bus.publishExternal(topic, message); }

    public RedisMessageBusBacking.RedisSubscription startExternalConsumer(String group, String consumer) {
        if (!(externalBacking instanceof RedisMessageBusBacking redisBacking)) {
            throw new IllegalStateException("Redis external backing is not configured");
        }
        if (externalSubscription != null) externalSubscription.close();
        externalSubscription = redisBacking.subscribe(group, consumer, bus);
        return externalSubscription;
    }

    public NautilusKernelConfig config() { return config; }
    public Clock clock() { return clock; }
    public Cache cache() { return cache; }
    public Portfolio portfolio() { return portfolio; }
    public RiskEngine riskEngine() { return riskEngine; }
    public ExecutionEngine executionEngine() { return executionEngine; }

    private void publishLiveMarketData(Object data) {
        inputSequence++;
        if (data instanceof Bar bar) {
            clock.setTimestampNs(bar.tsInit());
            dataEngine.publishBar(bar);
        } else if (data instanceof MarketDataSnapshot snapshot) {
            clock.setTimestampNs(snapshot.tsInit());
            dataEngine.publishMarketData(snapshot);
        } else if (data instanceof OrderBookSnapshot snapshot) {
            clock.setTimestampNs(snapshot.tsInit());
            dataEngine.publishOrderBook(snapshot);
        } else if (data instanceof TradeTick trade) {
            clock.setTimestampNs(trade.tsInit());
            dataEngine.publishTradeTick(trade);
        } else if (data instanceof OrderBookDelta delta) {
            clock.setTimestampNs(delta.tsInit());
            dataEngine.publishOrderBookDelta(delta);
        } else if (data instanceof OrderBookL3Snapshot snapshot) {
            clock.setTimestampNs(snapshot.tsInit());
            dataEngine.publishOrderBookL3(snapshot);
        } else if (data instanceof OrderBookL3Delta delta) {
            clock.setTimestampNs(delta.tsInit());
            dataEngine.publishOrderBookL3Delta(delta);
        } else if (data instanceof FxRateUpdate update) {
            clock.setTimestampNs(update.tsInit());
            dataEngine.publishFxRate(update);
        } else if (data instanceof FundingRateUpdate update) {
            clock.setTimestampNs(update.tsInit());
            dataEngine.publishFundingRate(update);
        } else {
            bus.publish(data);
        }
    }

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