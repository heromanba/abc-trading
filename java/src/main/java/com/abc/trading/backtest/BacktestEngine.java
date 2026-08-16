package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.events.CsvEventLogger;
import com.abc.trading.events.Event;
import com.abc.trading.events.EventLogger;
import com.abc.trading.events.EventType;
import com.abc.trading.execution.OrderAccepted;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.portfolio.PositionUpdate;
import com.abc.trading.system.ComponentState;
import com.abc.trading.system.NautilusKernel;
import com.abc.trading.trading.StrategyHandler;

import java.nio.file.Path;

/** JPype-friendly backtest library boundary for Python-owned strategies. */
public final class BacktestEngine implements AutoCloseable {
    private final NautilusKernel kernel = new NautilusKernel();
    private final EventLogger logger;
    private long lifecycleSequence;
    private boolean started;

    public BacktestEngine(String outputPath) {
        this.logger = new CsvEventLogger(Path.of(outputPath));
        kernel.bus().subscribe(OrderIntent.class, intent -> logger.log(new Event(
            intent.inputSequence(), ++lifecycleSequence, intent.marketTimestamp(),
            intent.symbol(), OrderIntent.class.getSimpleName(), EventType.ORDER_SUBMIT,
            intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
            intent.price(), intent.quantity(), intent.currentPosition(), intent.realizedPnl())), 100);
        kernel.bus().subscribe(OrderAccepted.class, accepted -> {
            OrderIntent intent = accepted.order();
            logger.log(new Event(
                intent.inputSequence(), ++lifecycleSequence, intent.marketTimestamp(),
                intent.symbol(), OrderAccepted.class.getSimpleName(), EventType.ORDER_ACCEPT,
                intent.strategyId(), intent.side(), intent.correlationId(), intent.orderId(),
                intent.price(), intent.quantity(), intent.currentPosition(), intent.realizedPnl()));
        });
        kernel.bus().subscribe(OrderFill.class, fill -> logger.log(new Event(
            fill.inputSequence(), ++lifecycleSequence, fill.marketTimestamp(), fill.symbol(),
            OrderFill.class.getSimpleName(), EventType.ORDER_FILL, fill.strategyId(), fill.side(),
            fill.correlationId(), fill.orderId(), fill.price(), fill.quantity(),
            fill.position(), fill.realizedPnl())), 100);
        kernel.bus().subscribe(PositionUpdate.class, update -> logger.log(new Event(
            update.inputSequence(), ++lifecycleSequence, update.marketTimestamp(), update.symbol(),
            PositionUpdate.class.getSimpleName(), EventType.POSITION_UPDATE, "", SignalDirection.HOLD,
            "", update.orderId(), 0.0, update.quantity(), update.position(), update.realizedPnl())));
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

    public void start() {
        kernel.start();
        started = true;
    }

    public void addStrategy(String symbol, StrategyHandler strategy) {
        if (started) throw new IllegalStateException("Cannot add strategies after start");
        kernel.addStrategy(symbol, strategy);
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

    public void submitMarketOrder(String strategyId, String symbol, long marketTimestamp,
                                  int sequence, String side, int quantity, double price) {
        if (!started) throw new IllegalStateException("Engine must be started before submitting orders");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        SignalDirection direction = SignalDirection.valueOf(side);
        int existingPosition = position(symbol);
        int targetPosition = direction == SignalDirection.BUY
                ? existingPosition + quantity
                : existingPosition - quantity;
        String correlationId = symbol + "-" + marketTimestamp + "-" + sequence;
        logger.log(new Event(
            kernel.currentInputSequence(), ++lifecycleSequence, marketTimestamp, symbol,
            "StrategySignal", EventType.SIGNAL, strategyId, direction,
                correlationId, "", price, 0, existingPosition, 0.0));
        kernel.bus().publish(new OrderIntent(
            strategyId, symbol, kernel.currentInputSequence(), marketTimestamp, correlationId,
            com.abc.trading.execution.DeterministicOrderId.fromCorrelation(correlationId),
            direction, quantity, price, targetPosition, 0.0));
    }

    @Override
    public void close() {
        kernel.close();
        logger.close();
    }
}
