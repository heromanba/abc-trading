package com.abc.trading.msgbus;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
