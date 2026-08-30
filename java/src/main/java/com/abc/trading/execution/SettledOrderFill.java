package com.abc.trading.execution;

import java.math.BigDecimal;

public record SettledOrderFill(OrderFill fill, BigDecimal position, double realizedPnl) {
	public SettledOrderFill(OrderFill fill, int position, double realizedPnl) {
		this(fill, BigDecimal.valueOf(position), realizedPnl);
	}
}
