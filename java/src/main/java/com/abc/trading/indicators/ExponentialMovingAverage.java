package com.abc.trading.indicators;

/** Primitive-backed exponential moving average. */
public final class ExponentialMovingAverage {
    private final double alpha;
    private double value;
    private long count;

    public ExponentialMovingAverage(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        this.alpha = 2.0 / (period + 1.0);
    }

    public double update(double input) {
        if (!Double.isFinite(input)) {
            throw new IllegalArgumentException("input must be finite");
        }
        value = count == 0 ? input : value + alpha * (input - value);
        count++;
        return value;
    }

    public double value() {
        return value;
    }

    public long count() {
        return count;
    }
}
