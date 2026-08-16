package com.abc.trading.trading;

public interface Actor {
    default void onStart() {
    }

    default void onStop() {
    }

    default void onReset() {
    }
}
