package com.abc.trading.data;

public interface DataClient {
    String clientId();

    default void start() {
    }

    default void stop() {
    }

    default void reset() {
    }
}
