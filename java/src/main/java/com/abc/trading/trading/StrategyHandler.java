package com.abc.trading.trading;

import com.abc.trading.data.Bar;

/** Callback contract for Python-owned strategies driven by the Java event loop. */
public interface StrategyHandler {
    default void onStart() {
    }

    void onBar(Bar bar);

    default void onBarWithContext(Bar bar, StrategyContext context) {
        context.onBar(bar);
        onBar(bar);
    }

    default void onStop() {
    }
}
