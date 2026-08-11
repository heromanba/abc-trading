package com.abc.trading.events;

public interface EventLogger extends AutoCloseable {
    void log(Event event);

    @Override
    void close();
}
