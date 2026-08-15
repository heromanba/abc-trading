package com.abc.trading.cache;

import com.abc.trading.execution.OrderIntent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic in-memory runtime state store. */
public final class Cache {
    private final Map<String, String> instruments = new LinkedHashMap<>();
    private final Map<String, Integer> positions = new LinkedHashMap<>();
    private final Map<String, OrderIntent> orders = new LinkedHashMap<>();

    public void addInstrument(String symbol, String venue) {
        instruments.put(symbol, venue);
        positions.putIfAbsent(symbol, 0);
    }

    public boolean hasInstrument(String symbol) {
        return instruments.containsKey(symbol);
    }

    public int position(String symbol) {
        return positions.getOrDefault(symbol, 0);
    }

    public void updatePosition(String symbol, int position) {
        positions.put(symbol, position);
    }

    public void recordOrder(OrderIntent order) {
        orders.put(order.orderId(), order);
    }

    public Map<String, Integer> positions() {
        return Map.copyOf(positions);
    }

    public Map<String, OrderIntent> orders() {
        return Map.copyOf(orders);
    }
}