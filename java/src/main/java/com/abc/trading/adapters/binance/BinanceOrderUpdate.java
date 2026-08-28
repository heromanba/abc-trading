package com.abc.trading.adapters.binance;

import java.math.BigDecimal;

/** Binance ORDER_TRADE_UPDATE normalized to the execution lifecycle vocabulary. */
public record BinanceOrderUpdate(
        String symbol,
        long eventTimeMs,
        long orderId,
        String clientOrderId,
        String side,
        String orderType,
        String timeInForce,
        String executionType,
        String orderStatus,
        BigDecimal lastQuantity,
        BigDecimal lastPrice,
        BigDecimal commission,
        String commissionAsset,
        boolean reduceOnly) {
    public boolean isTrade() { return "TRADE".equals(executionType); }

    public boolean isTerminal() {
        return "FILLED".equals(orderStatus) || "CANCELED".equals(orderStatus)
                || "REJECTED".equals(orderStatus) || "EXPIRED".equals(orderStatus);
    }
}
