package com.abc.trading.data;

import java.math.BigDecimal;
import java.math.MathContext;

/** Deterministic Black-Scholes pricing using double-special-function evaluation and Decimal outputs. */
public final class OptionPricing {
    private static final MathContext CONTEXT = MathContext.DECIMAL128;
    private OptionPricing() { }

    public static OptionGreeks blackScholes(OptionSpec option, Price spot, BigDecimal volatility,
            BigDecimal riskFreeRate, BigDecimal timeToExpiryYears) {
        requirePositive(spot.asDecimal(), "spot");
        requirePositive(volatility, "volatility");
        if (timeToExpiryYears.signum() <= 0) {
            BigDecimal payoff = option.intrinsicValue(spot);
            BigDecimal delta = option.right() == OptionRight.CALL ? BigDecimal.ONE : BigDecimal.ONE.negate();
            return new OptionGreeks(payoff, delta, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        double s = spot.asDouble();
        double k = option.strike().asDouble();
        double sigma = volatility.doubleValue();
        double rate = riskFreeRate.doubleValue();
        double time = timeToExpiryYears.doubleValue();
        double sqrtTime = Math.sqrt(time);
        double d1 = (Math.log(s / k) + (rate + 0.5 * sigma * sigma) * time) / (sigma * sqrtTime);
        double d2 = d1 - sigma * sqrtTime;
        double nd1 = normalCdf(d1);
        double nd2 = normalCdf(d2);
        double pdf = normalPdf(d1);
        boolean call = option.right() == OptionRight.CALL;
        double discount = Math.exp(-rate * time);
        double theoretical = call ? s * nd1 - k * discount * nd2 : k * discount * normalCdf(-d2) - s * normalCdf(-d1);
        double delta = call ? nd1 : nd1 - 1.0;
        double gamma = pdf / (s * sigma * sqrtTime);
        double theta = call
                ? (-s * pdf * sigma / (2.0 * sqrtTime) - rate * k * discount * nd2) / 365.0
                : (-s * pdf * sigma / (2.0 * sqrtTime) + rate * k * discount * normalCdf(-d2)) / 365.0;
        double vega = s * pdf * sqrtTime / 100.0;
        double rho = call ? k * time * discount * nd2 / 100.0 : -k * time * discount * normalCdf(-d2) / 100.0;
        BigDecimal multiplier = option.contractMultiplier();
        return new OptionGreeks(
                BigDecimal.valueOf(theoretical).multiply(multiplier, CONTEXT),
                BigDecimal.valueOf(delta), BigDecimal.valueOf(gamma), BigDecimal.valueOf(theta),
                BigDecimal.valueOf(vega).multiply(multiplier, CONTEXT), BigDecimal.valueOf(rho).multiply(multiplier, CONTEXT));
    }

    private static double normalPdf(double x) { return Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI); }
    private static double normalCdf(double x) { return 0.5 * (1.0 + erf(x / Math.sqrt(2.0))); }

    private static double erf(double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
