package com.abc.trading.execution;

public record SettledOrderFill(OrderFill fill, int position, double realizedPnl) {
}
