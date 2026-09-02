package com.abc.trading.data;

import java.math.BigDecimal;

/** Funding rate observation used to settle open perpetual positions. */
public record FundingRateUpdate(
        String symbol,
        BigDecimal rate,
        Integer intervalMinutes,
        Long nextFundingTimestamp,
        long tsEvent,
        long tsInit,
        long sequence) {
    public FundingRateUpdate {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (rate == null) throw new IllegalArgumentException("rate is required");
        if (intervalMinutes != null && intervalMinutes <= 0) {
            throw new IllegalArgumentException("intervalMinutes must be positive");
        }
        if (nextFundingTimestamp != null && nextFundingTimestamp < 0) {
            throw new IllegalArgumentException("nextFundingTimestamp must be non-negative");
        }
    }

    public FundingRateUpdate(String symbol, BigDecimal rate, long tsEvent, long tsInit, long sequence) {
        this(symbol, rate, null, null, tsEvent, tsInit, sequence);
    }
}