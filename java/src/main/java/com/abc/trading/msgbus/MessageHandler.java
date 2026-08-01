package com.abc.trading.msgbus;

/**
 * Handler invoked for messages received by the in-memory message bus.
 */
@FunctionalInterface
public interface MessageHandler {
    void onMessage(MessageBusEvent event);
}
