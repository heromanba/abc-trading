package com.abc.trading.msgbus.types;

public record QuoteTick(String symbol, double bid, double ask, long ts) {}
