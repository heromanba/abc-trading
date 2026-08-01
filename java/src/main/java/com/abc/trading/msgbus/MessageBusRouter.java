package com.abc.trading.msgbus;

import java.util.List;

/**
 * Router interface for topic-based subscription matching.
 */
public interface MessageBusRouter {
    void subscribe(String topicPattern, MessageHandler handler);

    void unsubscribe(String topicPattern, MessageHandler handler);

    List<MessageHandler> route(String topic);
}
