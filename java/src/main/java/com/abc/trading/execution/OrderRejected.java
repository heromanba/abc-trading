package com.abc.trading.execution;

public record OrderRejected(OrderIntent order, String reason) { }