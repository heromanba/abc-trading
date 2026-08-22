package com.abc.trading.backtest;

import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.TickScheme;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.data.OrderBookDelta;
import com.abc.trading.events.CsvEventLogger;
import com.abc.trading.events.Event;
import com.abc.trading.events.EventLogger;
import com.abc.trading.events.EventType;
import com.abc.trading.execution.OrderAccepted;
import com.abc.trading.execution.LimitOrderAccepted;
import com.abc.trading.execution.LimitOrderDenied;
import com.abc.trading.execution.OrderDenied;
import com.abc.trading.execution.OrderRejected;
import com.abc.trading.execution.LimitOrderRejected;
import com.abc.trading.execution.OrderCanceled;
import com.abc.trading.execution.OrderCancelRejected;
import com.abc.trading.execution.OrderModified;
import com.abc.trading.execution.OrderModifyRejected;
import com.abc.trading.execution.OrderExpired;
import com.abc.trading.execution.OrderTriggered;
import com.abc.trading.execution.OrderEmulated;
import com.abc.trading.execution.OrderReleased;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TimeInForce;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.execution.TrailingOffsetType;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.SettledOrderFill;
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
        kernel.bus().subscribe(OrderRejected.class, rejected -> logOrderRejected(rejected.order()));
        kernel.bus().subscribe(LimitOrderRejected.class, rejected -> logLimitOrderRejected(rejected.order()));
        kernel.bus().subscribe(LimitOrderAccepted.class, accepted -> logLimitOrderAccepted(accepted.order()));
        kernel.bus().subscribe(LimitOrderDenied.class, denied -> logLimitOrderDenied(denied.order()));
        kernel.bus().subscribe(OrderFill.class, this::logOrderFill, 100);
        kernel.bus().subscribe(SettledOrderFill.class, this::logSettledOrderFill, 100);
        kernel.bus().subscribe(PositionUpdate.class, this::logPositionUpdate);
        kernel.bus().subscribe(OrderCanceled.class, event -> logCancel(event.command(), EventType.ORDER_CANCEL));
        kernel.bus().subscribe(OrderCancelRejected.class, event -> logCancel(event.command(), EventType.ORDER_CANCEL_REJECT));
        kernel.bus().subscribe(OrderModified.class, event -> logModify(event.command(), EventType.ORDER_MODIFY));
        kernel.bus().subscribe(OrderModifyRejected.class, event -> logModify(event.command(), EventType.ORDER_MODIFY_REJECT));
        kernel.bus().subscribe(OrderExpired.class, event -> logExpired(event));
        kernel.bus().subscribe(OrderTriggered.class, this::logTriggered);
        kernel.bus().subscribe(OrderEmulated.class, event -> logLocalOrder(event.orderId(), EventType.ORDER_EMULATED));
        kernel.bus().subscribe(OrderReleased.class, event -> logLocalOrder(event.orderId(), EventType.ORDER_RELEASED));
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

    private void logOrderRejected(OrderIntent intent) {
        log(new Event(intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(), intent.symbol(),
                OrderRejected.class.getSimpleName(), EventType.ORDER_REJECT, intent.strategyId(), intent.side(),
                intent.correlationId(), intent.orderId(), intent.price(), intent.quantity(),
                intent.currentPosition(), intent.realizedPnl()));
    }

    private void logLimitOrderRejected(LimitOrderIntent intent) {
        log(new Event(intent.inputSequence(), nextLifecycleSequence(), intent.marketTimestamp(), intent.symbol(),
                LimitOrderRejected.class.getSimpleName(), EventType.ORDER_REJECT, intent.strategyId(), intent.side(),
                intent.correlationId(), intent.orderId(), intent.limitPrice(), intent.quantity(),
                intent.currentPosition(), intent.realizedPnl()));
    }

    private void logCancel(CancelOrder command, EventType eventType) {
        log(new Event(0, nextLifecycleSequence(), command.timestampNs(), command.symbol(),
                eventType.name(), eventType, command.strategyId(), SignalDirection.HOLD,
                command.commandId(), command.clientOrderId(), 0.0, 0, 0, 0.0));
    }

    private void logModify(ModifyOrder command, EventType eventType) {
        log(new Event(0, nextLifecycleSequence(), command.timestampNs(), command.symbol(),
                eventType.name(), eventType, command.strategyId(), SignalDirection.HOLD,
                command.commandId(), command.clientOrderId(), command.price() == null ? 0.0 : command.price(),
                command.quantity() == null ? 0 : command.quantity(), 0, 0.0));
    }

    private void logExpired(OrderExpired event) {
        log(new Event(0, nextLifecycleSequence(), event.marketTimestamp(), event.symbol(),
                OrderExpired.class.getSimpleName(), EventType.ORDER_EXPIRE, event.strategyId(), event.side(),
                "", event.orderId(), event.price(), event.remainingQuantity(), 0, 0.0));
    }

    private void logTriggered(OrderTriggered event) {
        log(new Event(event.inputSequence(), nextLifecycleSequence(), event.marketTimestamp(), event.symbol(),
                OrderTriggered.class.getSimpleName(), EventType.ORDER_TRIGGER, event.strategyId(),
                SignalDirection.HOLD, "", event.orderId(), event.triggerPrice(), 0, 0, 0.0));
    }

    private void logLocalOrder(String orderId, EventType eventType) {
        log(new Event(0, nextLifecycleSequence(), 0, "", eventType.name(), eventType,
                "", SignalDirection.HOLD, "", orderId, 0.0, 0, 0, 0.0));
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
        // Raw fills are settled by ExecutionEngine before the canonical fill event is logged.
    }

    private void logSettledOrderFill(SettledOrderFill settledFill) {
        OrderFill fill = settledFill.fill();
        log(new Event(
                fill.inputSequence(), nextLifecycleSequence(), fill.marketTimestamp(), fill.symbol(),
                SettledOrderFill.class.getSimpleName(), EventType.ORDER_FILL, fill.strategyId(), fill.side(),
                fill.correlationId(), fill.orderId(), fill.price(), fill.quantity(),
                settledFill.position(), fill.realizedPnl(),
                fill.commission().amount(), fill.commission().currency(), fill.liquiditySide()));
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
        addInstrument(symbol, venue, TickScheme.fixed(0.01));
    }

    public void addInstrument(String symbol, String venue, double tickSize) {
        addInstrument(symbol, venue, TickScheme.fixed(tickSize));
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme) {
        if (started) throw new IllegalStateException("Cannot add instruments after start");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        kernel.addInstrument(symbol, venue, tickScheme);
    }

    public void addStrategy(String symbol, StrategyHandler strategy) {
        addStrategy(symbol, symbol, strategy);
    }

    public void addStrategy(String symbol, String strategyId, StrategyHandler strategy) {
        if (started) throw new IllegalStateException("Cannot add strategies after start");
        kernel.addStrategy(symbol, strategyId, strategy);
    }

    public void addVenue(String venue) {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        kernel.addVenue(venue);
    }

    public void setMaxFillQuantity(String venue, int quantity) {
        kernel.exchange(venue).setMaxFillQuantity(quantity);
    }

    public void submitStopMarketOrder(String strategyId, String symbol, String orderId,
            SignalDirection side, int quantity, long timestampNs, double triggerPrice,
            TriggerType triggerType) {
        kernel.bus().publish(new OrderIntent(strategyId, symbol, kernel.currentInputSequence(), timestampNs,
                orderId + "-corr", orderId, side, quantity, triggerPrice, kernel.portfolio().position(symbol),
                0.0, TimeInForce.GTC, 0L, triggerPrice, triggerType));
    }

    public void submitStopLimitOrder(String strategyId, String symbol, String orderId,
            SignalDirection side, int quantity, long timestampNs, double limitPrice,
            double triggerPrice, TriggerType triggerType) {
        kernel.bus().publish(new LimitOrderIntent(strategyId, symbol, kernel.currentInputSequence(), timestampNs,
                orderId + "-corr", orderId, side, quantity, limitPrice, kernel.portfolio().position(symbol),
                0.0, TimeInForce.GTC, 0L, triggerPrice, triggerType));
    }

            public void submitTrailingStopMarketOrder(String strategyId, String symbol, String orderId,
                SignalDirection side, int quantity, long timestampNs, double activationPrice,
                double trailingOffset, TrailingOffsetType offsetType, TriggerType triggerType) {
            kernel.bus().publish(new OrderIntent(strategyId, symbol, kernel.currentInputSequence(), timestampNs,
                orderId + "-corr", orderId, side, quantity, 0.0, kernel.portfolio().position(symbol),
                0.0, TimeInForce.GTC, 0L, 0.0, triggerType, activationPrice, trailingOffset, offsetType));
            }

            public void submitTrailingStopLimitOrder(String strategyId, String symbol, String orderId,
                SignalDirection side, int quantity, long timestampNs, double limitPrice,
                double activationPrice, double limitOffset, double trailingOffset,
                TrailingOffsetType offsetType, TriggerType triggerType) {
            kernel.bus().publish(new LimitOrderIntent(strategyId, symbol, kernel.currentInputSequence(), timestampNs,
                orderId + "-corr", orderId, side, quantity, limitPrice, kernel.portfolio().position(symbol),
                0.0, TimeInForce.GTC, 0L, 0.0, triggerType, activationPrice, trailingOffset,
                offsetType, limitOffset));
            }

    public String orderStatus(String orderId) {
        return kernel.executionEngine().orderState(orderId).status().name();
    }

    public void emulateOrder(String orderId) {
        kernel.executionEngine().emulateOrder(orderId);
    }

    public void releaseOrder(String orderId) {
        kernel.executionEngine().releaseOrder(orderId);
    }

    public void submitReleasedOrder(String orderId) {
        kernel.executionEngine().submitReleasedOrder(orderId);
    }

    public void triggerOrder(String orderId) {
        kernel.executionEngine().triggerOrder(orderId);
    }

    public void voidOrder(String orderId) {
        kernel.executionEngine().voidOrder(orderId);
    }

    public void addVenue(SimulatedVenueConfig config) {
        kernel.addVenue(config);
    }

    public boolean isStarted() {
        return started && kernel.state() == ComponentState.RUNNING;
    }

    public int position(String symbol) {
        return kernel.portfolio().position(symbol);
    }

    public void runBars(Bar[] bars) {
        if (!started) throw new IllegalStateException("Engine must be started before running");
        if (bars == null) throw new IllegalArgumentException("bars are required");

        kernel.runBars(bars);
    }

    public void runMarketData(MarketDataSnapshot[] snapshots) {
        if (!started) throw new IllegalStateException("Engine must be started before running");
        kernel.runMarketData(snapshots);
    }

    public void runOrderBooks(OrderBookSnapshot[] snapshots) {
        if (!started) throw new IllegalStateException("Engine must be started before running");
        kernel.runOrderBooks(snapshots);
    }

    public void runOrderBookDeltas(OrderBookDelta[] deltas) {
        if (!started) throw new IllegalStateException("Engine must be started before running");
        kernel.runOrderBookDeltas(deltas);
    }

    public void submitMarketOrder(String strategyId, String symbol, String orderId,
            SignalDirection side, int quantity, long timestampNs, double price) {
        kernel.bus().publish(new OrderIntent(strategyId, symbol, kernel.currentInputSequence(), timestampNs,
                orderId + "-corr", orderId, side, quantity, price, kernel.portfolio().position(symbol), 0.0));
    }

    public void submitLimitOrder(String strategyId, String symbol, String orderId,
            SignalDirection side, int quantity, long timestampNs, double limitPrice) {
        kernel.bus().publish(new LimitOrderIntent(strategyId, symbol, kernel.currentInputSequence(), timestampNs,
                orderId + "-corr", orderId, side, quantity, limitPrice, kernel.portfolio().position(symbol), 0.0));
    }

    public void start() {
        kernel.start();
        started = true;
    }

    @Override
    public void close() {
        kernel.close();
        logger.close();
    }
}
