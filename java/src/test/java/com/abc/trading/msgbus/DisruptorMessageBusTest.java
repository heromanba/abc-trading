package com.abc.trading.msgbus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisruptorMessageBusTest {
    @Test
    void deliversPublishedMessagesInRingSequenceOrder() throws Exception {
        RecordingRouter router = new RecordingRouter();
        List<String> received = new ArrayList<>();
        CountDownLatch delivered = new CountDownLatch(3);
        router.subscribe("data", event -> {
            received.add((String) event.payload());
            delivered.countDown();
        });

        try (DisruptorMessageBus bus = new DisruptorMessageBus(router, 8)) {
            bus.publish("data", "one");
            bus.publish("data", "two");
            bus.publish("data", "three");

            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("one", "two", "three"), received);
        }
    }

    @Test
    void appliesBackpressureWhenTheBoundedRingIsFull() throws Exception {
        RecordingRouter router = new RecordingRouter();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch thirdPublished = new CountDownLatch(1);
        router.subscribe("data", event -> {
            firstEntered.countDown();
            try {
                releaseFirst.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        });

        try (DisruptorMessageBus bus = new DisruptorMessageBus(router, 2)) {
            bus.publish("data", "one");
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            bus.publish("data", "two");

            Thread thirdPublisher = new Thread(() -> {
                bus.publish("data", "three");
                thirdPublished.countDown();
            });
            thirdPublisher.start();
            assertFalse(thirdPublished.await(100, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            assertTrue(thirdPublished.await(2, TimeUnit.SECONDS));
            thirdPublisher.join(2_000);
        }
    }

    @Test
    void closeDrainsPublishedMessagesAndRejectsFuturePublication() throws Exception {
        RecordingRouter router = new RecordingRouter();
        CountDownLatch delivered = new CountDownLatch(1);
        router.subscribe("data", event -> delivered.countDown());
        DisruptorMessageBus bus = new DisruptorMessageBus(router, 8);

        bus.publish("data", "message");
        bus.close();

        assertEquals(0, delivered.getCount());
        assertTrue(bus.isClosed());
        assertThrows(IllegalStateException.class, () -> bus.publish("data", "after-close"));
        bus.close();
    }

    @Test
    void exposesConsumerFailureAndRejectsSubsequentPublication() throws Exception {
        RecordingRouter router = new RecordingRouter();
        CountDownLatch failed = new CountDownLatch(1);
        router.subscribe("data", event -> {
            failed.countDown();
            throw new IllegalStateException("consumer failed");
        });

        try (DisruptorMessageBus bus = new DisruptorMessageBus(router, 8)) {
            bus.publish("data", "message");
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (bus.failure() == null && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertNotNull(bus.failure());
            assertThrows(IllegalStateException.class, () -> bus.publish("data", "after-failure"));
        }
    }

    @Test
    void requiresPowerOfTwoRingCapacity() {
        RecordingRouter router = new RecordingRouter();

        assertThrows(IllegalArgumentException.class, () -> new DisruptorMessageBus(router, 1));
        assertThrows(IllegalArgumentException.class, () -> new DisruptorMessageBus(router, 3));
    }

    private static final class RecordingRouter implements MessageBusRouter {
        private final Map<String, List<MessageHandler>> handlers = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void subscribe(String topicPattern, MessageHandler handler) {
            handlers.computeIfAbsent(topicPattern, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(handler);
        }

        @Override
        public void unsubscribe(String topicPattern, MessageHandler handler) {
            List<MessageHandler> topicHandlers = handlers.get(topicPattern);
            if (topicHandlers != null) topicHandlers.remove(handler);
        }

        @Override
        public List<MessageHandler> route(String topic) {
            return handlers.getOrDefault(topic, List.of());
        }
    }
}