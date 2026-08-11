package com.abc.trading.indicators;

/** Primitive-backed fixed-window simple moving average. */
public final class SimpleMovingAverage {
    private final double[] values;
    private int size;
    private int next;
    private double sum;

    public SimpleMovingAverage(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        values = new double[period];
    }

    public double update(double input) {
        if (!Double.isFinite(input)) {
            throw new IllegalArgumentException("input must be finite");
        }
        if (size == values.length) {
            sum -= values[next];
        } else {
            size++;
        }
        values[next] = input;
        sum += input;
        next = (next + 1) % values.length;
        return value();
    }

    public double value() {
        return size == 0 ? 0.0 : sum / size;
    }

    public long count() {
        return size;
    }

    public boolean initialized() {
        return size == values.length;
    }
}
