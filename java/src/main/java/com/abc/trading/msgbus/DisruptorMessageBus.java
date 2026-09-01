package com.abc.trading.msgbus;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Ordered same-JVM handoff from concurrent producers to one routing consumer. */
public final class DisruptorMessageBus implements AutoCloseable {
    public static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final long CLOSE_TIMEOUT_SECONDS = 5;

    private final MessageBusRouter router;
    private final Disruptor<PendingMessage> disruptor;
    private final RingBuffer<PendingMessage> ringBuffer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong consumedSequence = new AtomicLong(-1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    public DisruptorMessageBus(MessageBusRouter router) {
        this(router, DEFAULT_BUFFER_SIZE);
    }

    public DisruptorMessageBus(MessageBusRouter router, int bufferSize) {
        if (router == null) throw new IllegalArgumentException("router is required");
        if (bufferSize < 2 || Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of two and at least 2");
        }
        this.router = router;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "abc-trading-disruptor");
            thread.setDaemon(true);
            return thread;
        };
        this.disruptor = new Disruptor<>(
                PendingMessage::new,
                bufferSize,
                threadFactory,
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        this.disruptor.setDefaultExceptionHandler(new FailureHandler());
        this.disruptor.handleEventsWith(new RoutingHandler());
        this.ringBuffer = disruptor.start();
    }

    /** Publishes with backpressure when all ring-buffer slots are occupied. */
    public void publish(String topic, Object payload) {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
        ensureOpen();
        ringBuffer.publishEvent((event, sequence, arguments) ->
                event.set((String) arguments[0], arguments[1]), topic, payload);
    }

    public void subscribe(String topicPattern, MessageHandler handler) {
        ensureOpen();
        router.subscribe(topicPattern, handler);
    }

    public void unsubscribe(String topicPattern, MessageHandler handler) {
        ensureOpen();
        router.unsubscribe(topicPattern, handler);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public Throwable failure() {
        return failure.get();
    }

    /** Waits until all events accepted before this call have reached the consumer. */
    public void drain() {
        if (closed.get()) return;
        awaitConsumed(ringBuffer.getCursor());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        long targetSequence = ringBuffer.getCursor();
        awaitConsumed(targetSequence);
        disruptor.halt();
    }

    private void awaitConsumed(long targetSequence) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLOSE_TIMEOUT_SECONDS);
        while (consumedSequence.get() < targetSequence
                && failure.get() == null
                && System.nanoTime() < deadline) {
            Thread.yield();
        }
        if (failure.get() != null) {
            if (closed.get()) return;
            throw new IllegalStateException("Disruptor message bus consumer failed", failure.get());
        }
        if (consumedSequence.get() < targetSequence) {
            throw new IllegalStateException("Disruptor message bus did not drain before shutdown", failure.get());
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("message bus is closed");
        Throwable error = failure.get();
        if (error != null) throw new IllegalStateException("message bus consumer failed", error);
    }

    private final class RoutingHandler implements EventHandler<PendingMessage> {
        @Override
        public void onEvent(PendingMessage event, long sequence, boolean endOfBatch) {
            MessageBusEvent message = event.value();
            List<MessageHandler> handlers = router.route(message.topic());
            if (handlers != null) {
                for (MessageHandler handler : List.copyOf(handlers)) {
                    handler.onMessage(message);
                }
            }
            event.clear();
            consumedSequence.set(sequence);
        }
    }

    private final class FailureHandler implements ExceptionHandler<PendingMessage> {
        @Override
        public void handleEventException(Throwable error, long sequence, PendingMessage event) {
            failure.compareAndSet(null, error);
            disruptor.halt();
        }

        @Override
        public void handleOnStartException(Throwable error) {
            failure.compareAndSet(null, error);
        }

        @Override
        public void handleOnShutdownException(Throwable error) {
            failure.compareAndSet(null, error);
        }
    }

    private static final class PendingMessage {
        private String topic;
        private Object payload;

        private void set(String topic, Object payload) {
            this.topic = topic;
            this.payload = payload;
        }

        private MessageBusEvent value() {
            return new MessageBusEvent(topic, payload);
        }

        private void clear() {
            topic = null;
            payload = null;
        }
    }
}
