package com.abc.trading.data;

import java.util.Arrays;

/** Instrument-owned tick rules, either fixed or price-tiered. */
public final class TickScheme {
    private final double fixedTickSize;
    private final PriceTier[] tiers;

    private TickScheme(double fixedTickSize, PriceTier[] tiers) {
        this.fixedTickSize = fixedTickSize;
        this.tiers = tiers;
    }

    public static TickScheme fixed(double tickSize) {
        validateTickSize(tickSize);
        return new TickScheme(tickSize, new PriceTier[0]);
    }

    public static TickScheme tiered(PriceTier... tiers) {
        if (tiers == null || tiers.length == 0) throw new IllegalArgumentException("tiers are required");
        PriceTier[] copy = tiers.clone();
        Arrays.sort(copy, (left, right) -> Double.compare(left.lowerInclusive(), right.lowerInclusive()));
        for (int index = 1; index < copy.length; index++) {
            if (copy[index].lowerInclusive() < copy[index - 1].upperExclusive()) {
                throw new IllegalArgumentException("tick tiers must not overlap");
            }
        }
        return new TickScheme(0.0, copy);
    }

    public double tickSize(double price) {
        if (!Double.isFinite(price) || price < 0.0) {
            throw new IllegalArgumentException("price must be finite and non-negative");
        }
        if (tiers.length == 0) return fixedTickSize;
        for (PriceTier tier : tiers) {
            if (tier.contains(price)) return tier.tickSize();
        }
        throw new IllegalArgumentException("price is outside registered tick tiers: " + price);
    }

    public boolean isTiered() {
        return tiers.length > 0;
    }

    private static void validateTickSize(double tickSize) {
        if (!Double.isFinite(tickSize) || tickSize <= 0.0) {
            throw new IllegalArgumentException("tickSize must be finite and positive");
        }
    }
}