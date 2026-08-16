package com.abc.trading.execution;

public record StaticLatencyModel(
        long baseLatencyNs,
        long insertExtraNs,
        long updateExtraNs,
        long deleteExtraNs) implements LatencyModel {
    public StaticLatencyModel {
        if (baseLatencyNs < 0 || insertExtraNs < 0 || updateExtraNs < 0 || deleteExtraNs < 0) {
            throw new IllegalArgumentException("latencies must be non-negative");
        }
    }

    public static StaticLatencyModel zero() {
        return new StaticLatencyModel(0, 0, 0, 0);
    }

    @Override
    public long getInsertLatencyNs() {
        return baseLatencyNs + insertExtraNs;
    }

    @Override
    public long getUpdateLatencyNs() {
        return baseLatencyNs + updateExtraNs;
    }

    @Override
    public long getDeleteLatencyNs() {
        return baseLatencyNs + deleteExtraNs;
    }

    @Override
    public long getBaseLatencyNs() {
        return baseLatencyNs;
    }
}
