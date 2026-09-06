package com.abc.trading.execution;

import com.abc.trading.data.Quantity;
import com.abc.trading.data.TradeTick;
import com.abc.trading.msgbus.Handler;
import com.abc.trading.msgbus.MessageBus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Volume-weighted participation execution driven by trade-tick volume. */
public final class VwapExecutionAlgorithm extends ExecutionAlgorithm {
    private final VwapExecutionConfig config;
    private final Handler<TradeTick> tradeHandler = this::onTrade;

    public VwapExecutionAlgorithm(MessageBus bus, VwapExecutionConfig config) {
        super(bus, config.algorithmId(), config.symbol(), config.side(),
                config.targetQuantity(), config.timeInForce());
        this.config = config;
    }

    @Override
    protected void onStart() {
        bus.subscribe(TradeTick.class, tradeHandler, 100);
    }

    @Override
    protected void onStop() {
        bus.unsubscribe(TradeTick.class, tradeHandler);
    }

    private void onTrade(TradeTick trade) {
        if (!isActive() || !config.symbol().equals(trade.symbol())
                || trade.tsInit() < config.startTimestampNs()
                || trade.tsInit() > config.endTimestampNs()) return;

        observeVolume(trade.quantity().asDecimal());
        BigDecimal budget = observedVolume().multiply(config.participationRate());
        BigDecimal submitted = submittedQuantity().asDecimal();
        BigDecimal available = budget.subtract(submitted);
        int precision = targetQuantity.precision();
        BigDecimal sliceValue = available.setScale(precision, RoundingMode.DOWN);
        BigDecimal minimum = config.minimumSlice().asDecimal();
        if (sliceValue.compareTo(minimum) < 0) {
            setLastTimestampNs(trade.tsInit());
            return;
        }
        sliceValue = sliceValue.min(config.maximumSlice().asDecimal())
                .min(remainingQuantity().asDecimal());
        if (sliceValue.signum() > 0) {
            submitChild(Quantity.fromDecimal(sliceValue, precision), trade.price(), trade.tsInit());
        }
        setLastTimestampNs(trade.tsInit());
    }
}
