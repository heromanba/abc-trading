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
    private final Map<Class<?>, List<Handler<?>>> typedHandlers = new LinkedHashMap<>();
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
        if (cls == null) throw new IllegalArgumentException("message type is required");
        if (handler == null) throw new IllegalArgumentException("handler is required");
        List<Handler<?>> handlers = typedHandlers.computeIfAbsent(cls, k -> new ArrayList<>());
        if (!handlers.contains(handler)) handlers.add(handler);
    }

    public <T> void unsubscribe(Class<T> cls, Handler<T> handler) {
        List<Handler<?>> list = typedHandlers.get(cls);
        if (list == null) return;
        list.remove(handler);
        if (list.isEmpty()) typedHandlers.remove(cls);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T msg) {
        Class<?> cls = msg.getClass();
        List<Handler<?>> handlers = typedHandlers.get(cls);
        if (handlers == null || handlers.isEmpty()) return;
        for (Handler<?> h : List.copyOf(handlers)) {
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
