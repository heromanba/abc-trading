package com.abc.trading.msgbus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Disruptor handoff for feed callbacks before they enter the trading loop. */
public final class DisruptorMarketDataIngress implements AutoCloseable {
    private final DisruptorMessageBus bus;

    public DisruptorMarketDataIngress(Consumer<Object> consumer) {
        this(consumer, DisruptorMessageBus.DEFAULT_BUFFER_SIZE);
    }

    public DisruptorMarketDataIngress(Consumer<Object> consumer, int bufferSize) {
        Objects.requireNonNull(consumer, "consumer");
        MessageHandler dispatcher = event -> consumer.accept(event.payload());
        MessageBusRouter router = new MessageBusRouter() {
            @Override
            public void subscribe(String topicPattern, MessageHandler handler) {
                throw new UnsupportedOperationException("ingress subscriptions are not supported");
            }

            @Override
            public void unsubscribe(String topicPattern, MessageHandler handler) {
                throw new UnsupportedOperationException("ingress subscriptions are not supported");
            }

            @Override
            public List<MessageHandler> route(String topic) {
                return List.of(dispatcher);
            }
        };
        bus = new DisruptorMessageBus(router, bufferSize);
    }

    public void publish(Object marketData) {
        if (marketData == null) throw new IllegalArgumentException("marketData is required");
        bus.publish(marketData.getClass().getName(), marketData);
    }

    public boolean isClosed() {
        return bus.isClosed();
    }

    public Throwable failure() {
        return bus.failure();
    }

    public void drain() {
        bus.drain();
    }

    @Override
    public void close() {
        bus.close();
    }
}
