package com.abc.trading.msgbus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal typed message bus implementation inspired by nautilus_trader design.
 */
public class MessageBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Handler<?>>> typedHandlers = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> nameToClass = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<Object>> correlation = new ConcurrentHashMap<>();
    private final Serializer serializer;

    public MessageBus(Serializer serializer) {
        this.serializer = serializer;
    }

    public <T> void registerType(String name, Class<T> cls) {
        nameToClass.put(name, cls);
    }

    public <T> void subscribe(Class<T> cls, Handler<T> handler) {
        typedHandlers.computeIfAbsent(cls, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public <T> void unsubscribe(Class<T> cls, Handler<T> handler) {
        List<Handler<?>> list = typedHandlers.get(cls);
        if (list != null) list.remove(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T msg) {
        Class<?> cls = msg.getClass();
        List<Handler<?>> handlers = typedHandlers.get(cls);
        if (handlers == null || handlers.isEmpty()) return;
        for (Handler<?> h : handlers) {
            ((Handler<T>) h).handle(msg);
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
