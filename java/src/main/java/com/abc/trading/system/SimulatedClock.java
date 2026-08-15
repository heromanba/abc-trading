package com.abc.trading.system;

public final class SimulatedClock implements Clock {
    private long timestampNs;

    @Override
    public long timestampNs() {
        return timestampNs;
    }

    @Override
    public void setTimestampNs(long timestampNs) {
        this.timestampNs = timestampNs;
    }
}