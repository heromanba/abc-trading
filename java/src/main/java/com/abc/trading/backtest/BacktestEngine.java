package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.events.CsvEventLogger;
import com.abc.trading.events.Event;
import com.abc.trading.events.EventLogger;
import com.abc.trading.events.EventType;
import com.abc.trading.execution.OrderAccepted;
import com.abc.trading.execution.LimitOrderAccepted;
import com.abc.trading.execution.LimitOrderDenied;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderDenied;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.portfolio.PositionUpdate;
import com.abc.trading.system.ComponentState;
import com.abc.trading.system.NautilusKernel;
import com.abc.trading.trading.StrategyHandler;
import com.abc.trading.trading.StrategySignal;

import java.nio.file.Path;

/** JPype-friendly backtest library boundary for Python-owned strategies. */
public final class BacktestEngine implements AutoCloseable {
    private final NautilusKernel kernel = new NautilusKernel();
    private final EventLogger logger;
    private long lifecycleSequence;
    private boolean started;

    public BacktestEngine(String outputPath) {
        this.logger = new CsvEventLogger(Path.of(outputPath));
        registerEventLoggers();
    }

    private void registerEventLoggers() {
        kernel.bus().subscribe(OrderIntent.class, this::logOrderSubmit, 100);
        kernel.bus().subscribe(StrategySignal.class, this::logStrategySignal, 100);
        kernel.bus().subscribe(OrderAccepted.class, accepted -> logOrderAccepted(accepted.order()));
        kernel.bus().subscribe(OrderDenied.class, denied -> logOrderDenied(denied.order()));
        kernel.bus().subscribe(LimitOrderAccepted.class, accepted -> logLimitOrderAccepted(accepted.order()));
        kernel.bus().subscribe(LimitOrderDenied.class, denied -> logLimitOrderDenied(denied.order()));
        kernel.bus().subscribe(OrderFill.class, this::logOrderFill, 100);
        kernel.bus().subscribe(PositionUpdate.class, this::logPositionUpdate);
    }

    private void logOrderSubmit(OrderIntent intent) {
        log(new Event(
                intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(),
                intent.symbol(), OrderIntent.class.getSimpleName(), EventType.ORDER_SUBMIT,
                intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
                intent.price(), intent.quantity(), intent.currentPosition(), intent.realizedPnl()));
    }

    private void logStrategySignal(StrategySignal signal) {
        log(new Event(
                signal.inputSequence(), nextLifecycleSequence(), signal.marketTimestamp(),
                signal.symbol(), StrategySignal.class.getSimpleName(), EventType.SIGNAL,
                signal.strategyId(), signal.side(), signal.correlationId(), "", signal.price(),
                0, signal.currentPosition(), 0.0));
    }

    private void logOrderAccepted(OrderIntent intent) {
        log(new Event(
                intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(),
                intent.symbol(), OrderAccepted.class.getSimpleName(), EventType.ORDER_ACCEPT,
                intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
                intent.price(), intent.quantity(), intent.currentPosition(), intent.realizedPnl()));
    }

    private void logOrderDenied(OrderIntent intent) {
        log(new Event(
                intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(),
                intent.symbol(), OrderDenied.class.getSimpleName(), EventType.ORDER_DENY,
                intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
                intent.price(), intent.quantity(), intent.currentPosition(), intent.realizedPnl()));
    }

    private void logLimitOrderAccepted(LimitOrderIntent intent) {
        log(new Event(
                intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(),
                intent.symbol(), LimitOrderAccepted.class.getSimpleName(), EventType.ORDER_LIMIT_ACCEPT,
                intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
                intent.limitPrice(), intent.quantity(), intent.currentPosition(), intent.realizedPnl()));
    }

    private void logLimitOrderDenied(LimitOrderIntent intent) {
        log(new Event(
                intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(),
                intent.symbol(), LimitOrderDenied.class.getSimpleName(), EventType.ORDER_DENY,
                intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
                intent.limitPrice(), intent.quantity(), intent.currentPosition(), intent.realizedPnl()));
    }

    private void logOrderFill(OrderFill fill) {
        log(new Event(
                fill.inputSequence(), nextLifecycleSequence(), fill.marketTimestamp(), fill.symbol(),
                OrderFill.class.getSimpleName(), EventType.ORDER_FILL, fill.strategyId(), fill.side(),
                fill.correlationId(), fill.orderId(), fill.price(), fill.quantity(),
                kernel.portfolio().position(fill.symbol()), kernel.portfolio().realizedPnl(fill.symbol()),
                fill.commission().amount(), fill.commission().currency()));
    }

    private void logPositionUpdate(PositionUpdate update) {
        log(new Event(
                update.inputSequence(), nextLifecycleSequence(), update.marketTimestamp(), update.symbol(),
                PositionUpdate.class.getSimpleName(), EventType.POSITION_UPDATE, "", SignalDirection.HOLD,
                "", update.orderId(), 0.0, update.quantity(), update.position(), update.realizedPnl()));
    }

    private void log(Event event) {
        logger.log(event);
    }

    private long nextLifecycleSequence() {
        return ++lifecycleSequence;
    }

    public void addInstrument(String symbol, String venue) {
        if (started) throw new IllegalStateException("Cannot add instruments after start");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        kernel.addInstrument(symbol, venue);
    }

    public void addVenue(String venue) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        kernel.addVenue(venue);
    }

    public void addVenue(SimulatedVenueConfig config) {
        kernel.addVenue(config);
    }

    public void start() {
        kernel.start();
        started = true;
    }

    public void addStrategy(String symbol, StrategyHandler strategy) {
        addStrategy(symbol, symbol, strategy);
    }

    public void addStrategy(String symbol, String strategyId, StrategyHandler strategy) {
        if (started) throw new IllegalStateException("Cannot add strategies after start");
        kernel.addStrategy(symbol, strategyId, strategy);
    }

    public void runBars(Bar[] bars) {
        if (!started) throw new IllegalStateException("Engine must be started before running");
        if (bars == null) throw new IllegalArgumentException("bars are required");

        kernel.runBars(bars);
    }

    public boolean isStarted() {
        return started && kernel.state() == ComponentState.RUNNING;
    }

    public int position(String symbol) {
        return kernel.portfolio().position(symbol);
    }

    @Override
    public void close() {
        kernel.close();
        logger.close();
    }
}
