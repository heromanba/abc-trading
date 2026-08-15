package com.abc.trading.msgbus;

import java.util.LinkedHashMap;
import java.util.Map;

/** Type-safe exact endpoint map for synchronous point-to-point delivery. */
public final class TypedEndpointMap<T> {
    private final Map<String, Handler<T>> handlers = new LinkedHashMap<>();

    public void register(String endpoint, Handler<T> handler) {
        validateEndpoint(endpoint);
        if (handler == null) throw new IllegalArgumentException("handler is required");
        handlers.put(endpoint, handler);
    }

    public void deregister(String endpoint) {
        validateEndpoint(endpoint);
        handlers.remove(endpoint);
    }

    public boolean isRegistered(String endpoint) {
        validateEndpoint(endpoint);
        return handlers.containsKey(endpoint);
    }

    public boolean trySend(String endpoint, T message) {
        validateEndpoint(endpoint);
        if (message == null) throw new IllegalArgumentException("message is required");
        Handler<T> handler = handlers.get(endpoint);
        if (handler == null) return false;
        handler.handle(message);
        return true;
    }

    public void send(String endpoint, T message) {
        trySend(endpoint, message);
    }

    private static void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        if (endpoint.indexOf('*') >= 0 || endpoint.indexOf('?') >= 0) {
            throw new IllegalArgumentException("endpoints cannot contain wildcards");
        }
    }
}