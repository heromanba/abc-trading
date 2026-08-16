package com.abc.trading.execution;

public record LimitOrderDenied(LimitOrderIntent order, String reason) {
}
