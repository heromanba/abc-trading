package com.abc.trading.execution;

public record OrderTriggered(
	String orderId,
	String strategyId,
	String symbol,
	long inputSequence,
	long marketTimestamp,
		double triggerPrice) {
	public OrderTriggered(String orderId) {
		this(orderId, "", "", 0L, 0L, 0.0);
	}
}