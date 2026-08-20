package com.abc.trading.execution;

public record LimitOrderRejected(LimitOrderIntent order, String reason) { }