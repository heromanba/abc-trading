package com.abc.trading.data;

/** One half-open price range and its venue tick size. */
public record PriceTier(double lowerInclusive, double upperExclusive, double tickSize) {
    public PriceTier {
        if (!Double.isFinite(lowerInclusive) || lowerInclusive < 0.0) {
            throw new IllegalArgumentException("lowerInclusive must be finite and non-negative");
        }
        if (!(upperExclusive > lowerInclusive)
                && upperExclusive != Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("upperExclusive must exceed lowerInclusive");
        }
        if (!Double.isFinite(tickSize) || tickSize <= 0.0) {
            throw new IllegalArgumentException("tickSize must be finite and positive");
        }
    }

    public boolean contains(double price) {
        return price >= lowerInclusive && price < upperExclusive;
    }
}