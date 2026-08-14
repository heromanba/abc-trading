package com.abc.trading.msgbus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Typed topic router with wildcard matching, deterministic ordering, and caching. */
public final class TypedTopicRouter<T> {
    private final List<Subscription<T>> subscriptions = new ArrayList<>();
    private final Map<String, List<Integer>> matchingHandlerCache = new HashMap<>();

    public void subscribe(String topic, Handler<T> handler) {
        subscribe(topic, handler, 0);
    }

    public void subscribe(String topic, Handler<T> handler, int priority) {
        validatePattern(topic);
        if (handler == null) throw new IllegalArgumentException("handler is required");
        for (Subscription<T> subscription : subscriptions) {
            if (subscription.topic.equals(topic) && subscription.handler == handler) return;
        }
        subscriptions.add(new Subscription<>(topic, handler, priority, subscriptions.size()));
        subscriptions.sort(Subscription::compareDeliveryOrder);
        matchingHandlerCache.clear();
    }

    public void unsubscribe(String topic, Handler<T> handler) {
        validatePattern(topic);
        if (handler == null) return;
        subscriptions.removeIf(subscription ->
                subscription.topic.equals(topic) && subscription.handler == handler);
        matchingHandlerCache.clear();
    }

    public void publish(String topic, T message) {
        validateTopic(topic);
        if (message == null) throw new IllegalArgumentException("message is required");
        List<Integer> indexes = matchingHandlerCache.computeIfAbsent(topic, this::findMatchingIndexes);
        for (int index : List.copyOf(indexes)) {
            subscriptions.get(index).handler.handle(message);
        }
    }

    private List<Integer> findMatchingIndexes(String topic) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < subscriptions.size(); index++) {
            if (matches(topic, subscriptions.get(index).topic)) indexes.add(index);
        }
        return List.copyOf(indexes);
    }

    private static void validateTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (topic.indexOf('*') >= 0 || topic.indexOf('?') >= 0) {
            throw new IllegalArgumentException("published topics cannot contain wildcards");
        }
    }

    private static void validatePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern is required");
        }
    }

    private static boolean matches(String topic, String pattern) {
        int topicIndex = 0;
        int patternIndex = 0;
        int starIndex = -1;
        int matchIndex = 0;

        while (topicIndex < topic.length()) {
            if (patternIndex < pattern.length()
                    && (pattern.charAt(patternIndex) == '?'
                    || pattern.charAt(patternIndex) == topic.charAt(topicIndex))) {
                topicIndex++;
                patternIndex++;
            } else if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                matchIndex = topicIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                topicIndex = ++matchIndex;
            } else {
                return false;
            }
        }

        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static final class Subscription<T> {
        private final String topic;
        private final Handler<T> handler;
        private final int priority;
        private final long registrationSequence;

        private Subscription(String topic, Handler<T> handler, int priority, long registrationSequence) {
            this.topic = topic;
            this.handler = handler;
            this.priority = priority;
            this.registrationSequence = registrationSequence;
        }

        private static int compareDeliveryOrder(Subscription<?> left, Subscription<?> right) {
            int priorityOrder = Integer.compare(right.priority, left.priority);
            if (priorityOrder != 0) return priorityOrder;
            int patternOrder = left.topic.compareTo(right.topic);
            return patternOrder != 0
                ? patternOrder
                : Long.compare(left.registrationSequence, right.registrationSequence);
        }
    }
}