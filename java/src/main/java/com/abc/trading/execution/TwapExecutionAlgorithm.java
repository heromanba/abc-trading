package com.abc.trading.execution;

import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.Quantity;
import com.abc.trading.msgbus.Handler;
import com.abc.trading.msgbus.MessageBus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Time-weighted execution using timestamped market-data observations as schedule triggers. */
public final class TwapExecutionAlgorithm extends ExecutionAlgorithm {
    private final TwapExecutionConfig config;
    private final Handler<Bar> barHandler = this::onBar;
    private final Handler<MarketDataSnapshot> marketDataHandler = this::onMarketData;

    public TwapExecutionAlgorithm(MessageBus bus, TwapExecutionConfig config) {
        super(bus, config.algorithmId(), config.symbol(), config.side(),
                config.targetQuantity(), config.timeInForce());
        this.config = config;
    }

    @Override
    protected void onStart() {
        bus.subscribe(Bar.class, barHandler, 100);
        bus.subscribe(MarketDataSnapshot.class, marketDataHandler, 100);
    }

    @Override
    protected void onStop() {
        bus.unsubscribe(Bar.class, barHandler);
        bus.unsubscribe(MarketDataSnapshot.class, marketDataHandler);
    }

    private void onBar(Bar bar) {
        if (config.symbol().equals(bar.symbol())) schedule(bar.tsInit(), bar.close());
    }

    private void onMarketData(MarketDataSnapshot snapshot) {
        if (config.symbol().equals(snapshot.symbol())) schedule(snapshot.tsInit(), snapshot.last());
    }

    private void schedule(long timestampNs, double price) {
        if (!isActive() || timestampNs < config.startTimestampNs() || price <= 0.0) return;
        long dueSlices = timestampNs >= config.endTimestampNs()
                ? config.totalSlices()
                : Math.min(config.totalSlices(),
                        (timestampNs - config.startTimestampNs()) / config.sliceIntervalNs() + 1);
        while (scheduleIndex() < dueSlices && canSubmit()) {
            long remainingSlices = config.totalSlices() - scheduleIndex();
            Quantity remaining = remainingQuantity();
            BigDecimal sliceValue = remaining.asDecimal().divide(
                    BigDecimal.valueOf(remainingSlices), remaining.precision(), RoundingMode.DOWN);
            if (scheduleIndex() + 1 == config.totalSlices()) sliceValue = remaining.asDecimal();
            if (sliceValue.signum() > 0) {
                submitChild(Quantity.fromDecimal(sliceValue, remaining.precision()), price, timestampNs);
            }
            advanceSchedule();
        }
        setLastTimestampNs(timestampNs);
    }
}
