package com.abc.trading.msgbus;

/**
 * Represents a message bus event to be published through the in-memory bus.
 */
public record MessageBusEvent(String topic, Object payload) {
}
