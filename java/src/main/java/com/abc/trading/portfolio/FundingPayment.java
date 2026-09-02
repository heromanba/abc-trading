package com.abc.trading.portfolio;

import java.math.BigDecimal;

/** Settled funding cash flow for one open perpetual position. */
public record FundingPayment(
        String venue,
        String symbol,
        BigDecimal rate,
        BigDecimal notional,
        BigDecimal amount,
        String currency,
        long timestamp) {
}