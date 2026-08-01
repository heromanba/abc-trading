package com.abc.trading.msgbus;

/**
 * In-memory message bus implementation backed by LMAX Disruptor.
 */
public class DisruptorMessageBus {
    private final MessageBusRouter router;

    public DisruptorMessageBus(MessageBusRouter router) {
        this.router = router;
    }

    public void publish(String topic, Object payload) {
        MessageBusEvent event = new MessageBusEvent(topic, payload);
        // TODO: publish event to Disruptor ring buffer
    }

    public void subscribe(String topicPattern, MessageHandler handler) {
        router.subscribe(topicPattern, handler);
    }

    public void unsubscribe(String topicPattern, MessageHandler handler) {
        router.unsubscribe(topicPattern, handler);
    }
}
