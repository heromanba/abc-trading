package com.abc.trading.execution;

public record OrderDenied(OrderIntent order, String reason) {
}