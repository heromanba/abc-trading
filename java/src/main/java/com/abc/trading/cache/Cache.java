package com.abc.trading.cache;

import com.abc.trading.execution.OrderIntent;
import com.abc.trading.data.TickScheme;
import com.abc.trading.data.InstrumentSpec;
import com.abc.trading.data.MarginModelType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic in-memory runtime state store. */
public final class Cache {
    private final Map<String, String> instruments = new LinkedHashMap<>();
    private final Map<String, TickScheme> tickSchemes = new LinkedHashMap<>();
    private final Map<String, InstrumentSpec> instrumentsBySymbol = new LinkedHashMap<>();
    private final Map<String, Integer> positions = new LinkedHashMap<>();
    private final Map<String, OrderIntent> orders = new LinkedHashMap<>();

    public void addInstrument(String symbol, String venue) {
        addInstrument(symbol, venue, TickScheme.fixed(0.01));
    }

    public void addInstrument(String symbol, String venue, double tickSize) {
        addInstrument(symbol, venue, TickScheme.fixed(tickSize));
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme) {
        addInstrument(InstrumentSpec.defaults(symbol, venue, tickScheme));
    }

    public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate) {
        addInstrument(new InstrumentSpec(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
                marginInitialRate, marginMaintenanceRate));
    }

        public void addInstrument(String symbol, String venue, TickScheme tickScheme,
            String baseCurrency, String quoteCurrency, double marginInitialRate,
            double marginMaintenanceRate, MarginModelType marginModelType,
            double initialMarginPerUnit, double maintenanceMarginPerUnit) {
        addInstrument(new InstrumentSpec(symbol, venue, tickScheme, baseCurrency, quoteCurrency,
            marginInitialRate, marginMaintenanceRate, marginModelType,
            initialMarginPerUnit, maintenanceMarginPerUnit));
        }

    public void addInstrument(InstrumentSpec instrument) {
        String symbol = instrument.symbol();
        String venue = instrument.venue();
        TickScheme tickScheme = instrument.tickScheme();
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("venue is required");
        if (tickScheme == null) throw new IllegalArgumentException("tickScheme is required");
        instruments.put(symbol, venue);
        tickSchemes.put(symbol, tickScheme);
        instrumentsBySymbol.put(symbol, instrument);
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

    public InstrumentSpec instrument(String symbol) {
        InstrumentSpec instrument = instrumentsBySymbol.get(symbol);
        if (instrument == null) throw new IllegalArgumentException("Unknown instrument: " + symbol);
        return instrument;
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

    public Map<String, Integer> positionsForVenue(String venue) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : instruments.entrySet()) {
            if (entry.getValue().equals(venue) && positions.getOrDefault(entry.getKey(), 0) != 0) {
                result.put(entry.getKey(), positions.get(entry.getKey()));
            }
        }
        return Map.copyOf(result);
    }

    public Map<String, OrderIntent> orders() {
        return Map.copyOf(orders);
    }
}