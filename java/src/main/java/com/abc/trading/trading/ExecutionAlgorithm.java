package com.abc.trading.trading;

import com.abc.trading.execution.OrderIntent;

public interface ExecutionAlgorithm extends Actor {
    void execute(OrderIntent order);
}
