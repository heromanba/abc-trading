package com.abc.trading.msgbus;

@FunctionalInterface
public interface Handler<T> {
    void handle(T msg);
}
