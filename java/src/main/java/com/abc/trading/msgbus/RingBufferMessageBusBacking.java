package com.abc.trading.msgbus;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple in-process backing that uses a bounded ring buffer for outbound messages.
 *
 * This is a transport layer for external flow, not the in-memory typed bus itself.
 */
public class RingBufferMessageBusBacking implements MessageBusBacking {
    private final BlockingQueue<BusMessage> queue;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RingBufferMessageBusBacking(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void publish(BusMessage message) {
        if (isClosed()) {
            return;
        }

        boolean accepted = queue.offer(message);
        if (!accepted) {
            // Buffer full: drop the message or apply backpressure in a real implementation.
            System.err.println("MessageBus ring buffer full; dropping outgoing message");
        }
    }

    @Override
    public void close() {
        closed.set(true);
    }

    /**
     * Attempts to poll the next outbound message from the ring buffer.
     *
     * Consumers may use this from a dedicated reader thread to drain the buffer.
     */
    public BusMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * Returns the internal queue for custom consumption semantics.
     */
    public BlockingQueue<BusMessage> queue() {
        return queue;
    }
}
