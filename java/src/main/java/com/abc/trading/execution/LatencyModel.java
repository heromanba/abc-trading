package com.abc.trading.execution;

public interface LatencyModel {
    long getInsertLatencyNs();
    long getUpdateLatencyNs();
    long getDeleteLatencyNs();
    long getBaseLatencyNs();
}
