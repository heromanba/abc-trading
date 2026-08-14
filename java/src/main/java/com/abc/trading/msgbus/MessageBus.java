package com.abc.trading.msgbus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Minimal typed message bus implementation inspired by nautilus_trader design.
 */
public class MessageBus {
    private final Map<Class<?>, List<TypedSubscription<?>>> typedHandlers = new LinkedHashMap<>();
    private final Map<Class<?>, TypedTopicRouter<?>> typedTopicRouters = new LinkedHashMap<>();
    private final Map<String, Class<?>> nameToClass = new LinkedHashMap<>();
    private final Map<UUID, Consumer<Object>> correlation = new LinkedHashMap<>();
    private final Serializer serializer;

    public MessageBus(Serializer serializer) {
        this.serializer = serializer;
    }

    public <T> void registerType(String name, Class<T> cls) {
        nameToClass.put(name, cls);
    }

    public <T> void subscribe(Class<T> cls, Handler<T> handler) {
        subscribe(cls, handler, 0);
    }

    public <T> void subscribe(Class<T> cls, Handler<T> handler, int priority) {
        if (cls == null) throw new IllegalArgumentException("message type is required");
        if (handler == null) throw new IllegalArgumentException("handler is required");
        List<TypedSubscription<?>> handlers = typedHandlers.computeIfAbsent(cls, k -> new ArrayList<>());
        for (TypedSubscription<?> subscription : handlers) {
            if (subscription.handler == handler) return;
        }
        handlers.add(new TypedSubscription<>(handler, priority, handlers.size()));
        handlers.sort(TypedSubscription::compareDeliveryOrder);
    }

    public <T> void subscribe(String topic, Class<T> cls, Handler<T> handler) {
        router(cls).subscribe(topic, handler);
    }

    public <T> void subscribe(String topic, Class<T> cls, Handler<T> handler, int priority) {
        router(cls).subscribe(topic, handler, priority);
    }

    public <T> void subscribe(Class<T> cls, String topic, Handler<T> handler) {
        subscribe(topic, cls, handler);
    }

    public <T> void unsubscribe(Class<T> cls, Handler<T> handler) {
        List<TypedSubscription<?>> list = typedHandlers.get(cls);
        if (list == null) return;
        list.removeIf(subscription -> subscription.handler == handler);
        if (list.isEmpty()) typedHandlers.remove(cls);
    }

    public <T> void unsubscribe(String topic, Class<T> cls, Handler<T> handler) {
        TypedTopicRouter<?> router = typedTopicRouters.get(cls);
        if (router != null) typedRouter(router, cls).unsubscribe(topic, handler);
    }

    public <T> void unsubscribe(Class<T> cls, String topic, Handler<T> handler) {
        unsubscribe(topic, cls, handler);
    }

    public <T> void publish(String topic, T msg) {
        if (msg == null) throw new IllegalArgumentException("message is required");
        publishTopicUntyped(topic, msg.getClass(), msg);
    }

    public <T> TypedTopicRouter<T> router(Class<T> cls) {
        if (cls == null) throw new IllegalArgumentException("message type is required");
        return typedRouter(
                typedTopicRouters.computeIfAbsent(cls, ignored -> new TypedTopicRouter<>()),
                cls);
    }

    @SuppressWarnings("unchecked")
    private static <T> TypedTopicRouter<T> typedRouter(
            TypedTopicRouter<?> router,
            Class<T> cls) {
        return (TypedTopicRouter<T>) router;
    }

    private <T> void publishTopic(String topic, Class<T> cls, T msg) {
        TypedTopicRouter<?> router = typedTopicRouters.get(cls);
        if (router != null) typedRouter(router, cls).publish(topic, msg);
    }

    @SuppressWarnings("unchecked")
    private <T> void publishTopicUntyped(String topic, Class<?> cls, T msg) {
        publishTopic(topic, (Class<T>) cls, msg);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T msg) {
        Class<?> cls = msg.getClass();
        List<TypedSubscription<?>> handlers = typedHandlers.get(cls);
        if (handlers == null || handlers.isEmpty()) return;
        for (TypedSubscription<?> subscription : List.copyOf(handlers)) {
            ((Handler<T>) subscription.handler).handle(msg);
        }
    }

    private static final class TypedSubscription<T> {
        private final Handler<T> handler;
        private final int priority;
        private final long registrationSequence;

        private TypedSubscription(Handler<T> handler, int priority, long registrationSequence) {
            this.handler = handler;
            this.priority = priority;
            this.registrationSequence = registrationSequence;
        }

        private static int compareDeliveryOrder(TypedSubscription<?> left, TypedSubscription<?> right) {
            int priorityOrder = Integer.compare(right.priority, left.priority);
            return priorityOrder != 0
                    ? priorityOrder
                    : Long.compare(left.registrationSequence, right.registrationSequence);
        }
    }

    public void publishExternal(BusMessage m) throws Exception {
        Class<?> cls = nameToClass.get(m.getPayloadType());
        if (cls == null) return; // unknown type
        Object obj = serializer.deserialize(m.getPayload(), cls);
        publish(obj);
    }

    public void send(String endpoint, Object msg) {
        // simple endpoint registry could be added; for prototype, just publish by class
        publish(msg);
    }

    public UUID request(Object req, Consumer<Object> callback) {
        UUID id = UUID.randomUUID();
        correlation.put(id, callback);
        // in full impl, attach id to request object and send to endpoint
        return id;
    }

    public void response(UUID id, Object res) {
        Consumer<Object> cb = correlation.remove(id);
        if (cb != null) cb.accept(res);
    }
}
