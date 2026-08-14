package com.abc.trading.msgbus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessageBusTest {
    @Test
    void testMessageBusSkeletonExists() {
        MessageBusRouter router = new MessageBusRouter() {
            @Override
            public void subscribe(String topicPattern, MessageHandler handler) {
                // no-op
            }

            @Override
            public void unsubscribe(String topicPattern, MessageHandler handler) {
                // no-op
            }

            @Override
            public List<MessageHandler> route(String topic) {
                return Collections.emptyList();
            }
        };

        DisruptorMessageBus bus = new DisruptorMessageBus(router);
        assertNotNull(bus);
    }

    @Test
    void publishesSynchronouslyInRegistrationOrder() {
        MessageBus bus = new MessageBus(null);
        List<String> calls = new ArrayList<>();
        Handler<String> first = message -> calls.add("first:" + message);
        Handler<String> second = message -> calls.add("second:" + message);

        bus.subscribe(String.class, first);
        bus.subscribe(String.class, second);
        bus.publish("event");

        assertEquals(List.of("first:event", "second:event"), calls);
    }

    @Test
    void suppressesDuplicateSubscriptionsAndUnsubscribes() {
        MessageBus bus = new MessageBus(null);
        List<String> calls = new ArrayList<>();
        Handler<String> handler = calls::add;

        bus.subscribe(String.class, handler);
        bus.subscribe(String.class, handler);
        bus.publish("before");
        bus.unsubscribe(String.class, handler);
        bus.publish("after");

        assertEquals(List.of("before"), calls);
    }

    @Test
    void deliversHigherPriorityHandlersFirst() {
        MessageBus bus = new MessageBus(null);
        List<String> calls = new ArrayList<>();

        bus.subscribe(String.class, message -> calls.add("normal"));
        bus.subscribe(String.class, message -> calls.add("high"), 10);
        bus.publish("event");

        assertEquals(List.of("high", "normal"), calls);
    }

    @Test
    void routesTypedMessagesByTopicAndKeepsTopicsSeparate() {
        MessageBus bus = new MessageBus(null);
        List<String> calls = new ArrayList<>();
        Handler<String> handler = calls::add;

        bus.subscribe("data.bar.*", String.class, handler);
        bus.publish("data.bar.NVDA", "ignored");
        bus.publish("data.quote.AAPL", "ignored");
        bus.publish("data.bar.AAPL", "delivered");

        assertEquals(List.of("ignored", "delivered"), calls);
    }

    @Test
    void invalidatesTopicCacheAfterSubscriptionChanges() {
        MessageBus bus = new MessageBus(null);
        List<String> calls = new ArrayList<>();
        Handler<String> first = message -> calls.add("first:" + message);
        Handler<String> second = message -> calls.add("second:" + message);

        bus.subscribe("topic", String.class, first);
        bus.publish("topic", "one");
        bus.subscribe("topic", String.class, second);
        bus.publish("topic", "two");
        bus.unsubscribe("topic", String.class, first);
        bus.publish("topic", "three");

        assertEquals(
                List.of("first:one", "first:two", "second:two", "second:three"),
                calls);
    }

    @Test
    void rejectsWildcardPublishedTopicsButAcceptsWildcardPatterns() {
        MessageBus bus = new MessageBus(null);
        bus.subscribe("data.bar.????", String.class, message -> { });

        assertThrows(
                IllegalArgumentException.class,
                () -> bus.publish("data.*", "invalid"));
    }

    @Test
    void publishingWithoutSubscribersIsANoOp() {
        MessageBus bus = new MessageBus(null);

        bus.publish("unhandled");
    }

    @Test
    void reentrantPublishUsesStableHandlerSnapshot() {
        MessageBus bus = new MessageBus(null);
        List<String> calls = new ArrayList<>();
        Handler<String> first = message -> {
            calls.add("first:" + message);
            if (message.equals("outer")) bus.publish("inner");
        };
        Handler<String> second = message -> calls.add("second:" + message);

        bus.subscribe(String.class, first);
        bus.subscribe(String.class, second);
        bus.publish("outer");

        assertEquals(
                List.of("first:outer", "first:inner", "second:inner", "second:outer"),
                calls);
    }

    @Test
    void propagatesHandlerExceptions() {
        MessageBus bus = new MessageBus(null);
        bus.subscribe(String.class, message -> {
            throw new IllegalStateException("handler failed");
        });

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bus.publish("event"));

        assertEquals("handler failed", exception.getMessage());
    }
}
