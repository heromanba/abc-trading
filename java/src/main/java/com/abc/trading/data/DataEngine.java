package com.abc.trading.data;

import com.abc.trading.msgbus.MessageBus;

/** Publishes market data through typed topic routing. */
public final class DataEngine {
    private final MessageBus bus;

    public DataEngine(MessageBus bus) {
        this.bus = bus;
    }

    public void publishBar(Bar bar) {
        bus.publish("data.bar." + bar.symbol(), bar);
        bus.publish(bar);
    }

    public void publishMarketData(MarketDataSnapshot snapshot) {
        bus.publish("data.market." + snapshot.symbol(), snapshot);
        bus.publish(snapshot);
    }

    public void publishOrderBook(OrderBookSnapshot snapshot) {
        bus.publish("data.book." + snapshot.symbol(), snapshot);
        bus.publish(snapshot);
    }

    public void publishOrderBookDelta(OrderBookDelta delta) {
        bus.publish("data.book.delta." + delta.symbol(), delta);
        bus.publish(delta);
    }

    public void publishOrderBookL3(OrderBookL3Snapshot snapshot) {
        bus.publish("data.book.l3." + snapshot.symbol(), snapshot);
        bus.publish(snapshot);
    }

    public void publishOrderBookL3Delta(OrderBookL3Delta delta) {
        bus.publish("data.book.l3.delta." + delta.symbol(), delta);
        bus.publish(delta);
    }

    public void publishTradeTick(TradeTick trade) {
        bus.publish("data.trade." + trade.symbol(), trade);
        bus.publish(trade);
    }

    public void publishFxRate(FxRateUpdate update) {
        bus.publish("data.fx." + update.fromCurrency() + "." + update.toCurrency(), update);
        bus.publish(update);
    }
}