package com.abc.trading.cache;

import com.abc.trading.execution.OrderIntent;
import com.abc.trading.data.TickScheme;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic in-memory runtime state store. */
public final class Cache {
    private final Map<String, String> instruments = new LinkedHashMap<>();
    private final Map<String, TickScheme> tickSchemes = new LinkedHashMap<>();
    private final Map<String, Integer> positions = new LinkedHashMap<>();
    private final Map<String, OrderIntent> orders = new LinkedHashMap<>();

    public void addInstrument(String symbol, String venue) {
        addInstrument(symbol, venue, TickScheme.fixed(0.01));
    }

    public void addInstrument(String symbol, String venue, double tickSize) {
        addInstrument(symbol, venue, TickScheme.fixed(tickSize));
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (tickScheme == null) throw new IllegalArgumentException("tickScheme is required");
        instruments.put(symbol, venue);
        tickSchemes.put(symbol, tickScheme);
        positions.putIfAbsent(symbol, 0);
    }

    public boolean hasInstrument(String symbol) {
        return instruments.containsKey(symbol);
    }

    public String venue(String symbol) {
        String venue = instruments.get(symbol);
        if (venue == null) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        return venue;
    }

    public double tickSize(String symbol, double price) {
        TickScheme tickScheme = tickSchemes.get(symbol);
        if (tickScheme == null) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        return tickScheme.tickSize(price);
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