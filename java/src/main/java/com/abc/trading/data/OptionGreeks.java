package com.abc.trading.data;

import java.math.BigDecimal;

/** Black-Scholes option price and first/second-order sensitivities. */
public record OptionGreeks(
        BigDecimal theoreticalPrice,
        BigDecimal delta,
        BigDecimal gamma,
        BigDecimal theta,
        BigDecimal vega,
        BigDecimal rho) {
}
