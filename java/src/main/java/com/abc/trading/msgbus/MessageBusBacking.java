package com.abc.trading.msgbus;

/**
 * Transport abstraction for outbound message bus publications.
 *
 * A backing implementation may serialize and forward messages to an external
 * stream, a thread-safe in-process queue, or another node.
 */
public interface MessageBusBacking {
    /**
     * Returns true when the backing has been closed and no longer accepts messages.
     */
    boolean isClosed();

    /**
     * Publishes a serialized bus message into the backing transport.
     *
     * If the backing is full or closed, the implementation may drop the message.
     */
    void publish(BusMessage message);

    /**
     * Closes the backing and releases any resources.
     */
    void close();
}
