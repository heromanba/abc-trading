package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.events.CsvEventLogger;
import com.abc.trading.events.Event;
import com.abc.trading.events.EventLogger;
import com.abc.trading.events.EventType;
import com.abc.trading.execution.DeterministicOrderId;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.trading.StrategyHandler;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** JPype-friendly backtest library boundary for Python-owned strategies. */
public final class BacktestEngine implements AutoCloseable {
    private final MessageBus bus = new MessageBus(null);
    private final Map<String, Integer> positions = new LinkedHashMap<>();
    private final Map<String, String> instruments = new LinkedHashMap<>();
    private final Map<String, StrategyHandler> strategies = new LinkedHashMap<>();
    private final EventLogger logger;
    private boolean started;

    public BacktestEngine(String outputPath) {
        this.logger = new CsvEventLogger(Path.of(outputPath));
        bus.subscribe(OrderIntent.class, intent -> positions.put(intent.symbol(), intent.currentPosition()));
        bus.subscribe(OrderIntent.class, intent -> logger.log(new Event(
                intent.marketTimestamp(), EventType.ORDER_SUBMIT, intent.strategyId(),
                intent.side(), intent.correlationId(), intent.orderId(), intent.price(),
                intent.quantity(), intent.currentPosition(), intent.realizedPnl())));
    }

    public void addInstrument(String symbol, String venue) {
        if (started) throw new IllegalStateException("Cannot add instruments after start");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        instruments.put(symbol, venue);
        positions.putIfAbsent(symbol, 0);
    }

    public void addVenue(String venue) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
    }

    public void start() {
        started = true;
    }

    public void addStrategy(String symbol, StrategyHandler strategy) {
        if (started) throw new IllegalStateException("Cannot add strategies after start");
        if (!instruments.containsKey(symbol)) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        if (strategy == null) throw new IllegalArgumentException("strategy is required");
        if (strategies.putIfAbsent(symbol, strategy) != null) {
            throw new IllegalArgumentException("A strategy is already registered for: " + symbol);
        }
    }

    public void runBars(Bar[] bars) {
        if (!started) throw new IllegalStateException("Engine must be started before running");
        if (bars == null) throw new IllegalArgumentException("bars are required");

        strategies.values().forEach(StrategyHandler::onStart);
        try {
            Arrays.sort(bars, Comparator.comparingLong(Bar::tsInit).thenComparing(Bar::symbol));
            for (Bar bar : bars) {
                StrategyHandler strategy = strategies.get(bar.symbol());
                if (strategy != null) strategy.onBar(bar);
            }
        } finally {
            strategies.values().forEach(StrategyHandler::onStop);
        }
    }

    public boolean isStarted() {
        return started;
    }

    public int position(String symbol) {
        return positions.getOrDefault(symbol, 0);
    }

    public void submitMarketOrder(String strategyId, String symbol, long marketTimestamp,
                                  int sequence, String side, int quantity, double price) {
        if (!started) throw new IllegalStateException("Engine must be started before submitting orders");
        if (!instruments.containsKey(symbol)) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        SignalDirection direction = SignalDirection.valueOf(side);
        int existingPosition = position(symbol);
        int targetPosition = direction == SignalDirection.BUY
                ? existingPosition + quantity
                : existingPosition - quantity;
        String correlationId = symbol + "-" + marketTimestamp + "-" + sequence;
        logger.log(new Event(
                marketTimestamp, EventType.SIGNAL, strategyId, direction,
                correlationId, "", price, 0, existingPosition, 0.0));
        bus.publish(new OrderIntent(
                strategyId, symbol, marketTimestamp, correlationId,
                DeterministicOrderId.fromCorrelation(correlationId), direction,
                quantity, price, targetPosition, 0.0));
    }

    @Override
    public void close() {
        logger.close();
    }
}
