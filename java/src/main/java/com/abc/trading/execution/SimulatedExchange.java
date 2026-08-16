package com.abc.trading.execution;

import com.abc.trading.data.Bar;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal simulated venue using the latest bar close as the market price. */
public final class SimulatedExchange {
    private final VenueId venue;
    private final Map<String, Double> lastPrices = new LinkedHashMap<>();

    public SimulatedExchange(VenueId venue) {
        this.venue = venue;
    }

    public VenueId venue() {
        return venue;
    }

    public void processBar(Bar bar) {
        lastPrices.put(bar.symbol(), bar.close());
    }

    public double currentPrice(String symbol) {
        Double price = lastPrices.get(symbol);
        if (price == null) {
            throw new IllegalStateException("No current market price for " + symbol + " on " + venue.value());
        }
        return price;
    }
}