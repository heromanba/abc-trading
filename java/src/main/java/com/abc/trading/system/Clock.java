package com.abc.trading.system;

/** Clock abstraction corresponding to Nautilus's clock contract. */
public interface Clock {
    long timestampNs();

    void setTimestampNs(long timestampNs);
}