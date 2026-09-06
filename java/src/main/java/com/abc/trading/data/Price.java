package com.abc.trading.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable positive fixed-point price with explicit precision. */
public final class Price implements Comparable<Price> {
    private final long raw;
    private final int precision;

    private Price(long raw, int precision) {
        if (raw <= 0) throw new IllegalArgumentException("price must be positive");
        if (precision < 0 || precision > 18) throw new IllegalArgumentException("precision must be in 0..18");
        this.raw = raw;
        this.precision = precision;
    }

    public static Price fromRaw(long raw, int precision) { return new Price(raw, precision); }
    public static Price fromDecimal(BigDecimal value, int precision) {
        Objects.requireNonNull(value, "value");
        try {
            BigDecimal scaled = value.setScale(precision, RoundingMode.UNNECESSARY).movePointRight(precision);
            return fromRaw(scaled.longValueExact(), precision);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("price is not representable at precision " + precision, error);
        }
    }
    public static Price fromString(String value, int precision) { return fromDecimal(new BigDecimal(value), precision); }
    public long raw() { return raw; }
    public int precision() { return precision; }
    public BigDecimal asDecimal() { return BigDecimal.valueOf(raw, precision); }
    public double asDouble() { return asDecimal().doubleValue(); }
    public Price add(Price other) {
        int target = Math.max(precision, other.precision);
        return fromDecimal(asDecimal().add(other.asDecimal()), target);
    }
    public Price subtract(Price other) {
        int target = Math.max(precision, other.precision);
        return fromDecimal(asDecimal().subtract(other.asDecimal()), target);
    }
    @Override public int compareTo(Price other) { return asDecimal().compareTo(other.asDecimal()); }
    @Override public boolean equals(Object other) { return other instanceof Price p && compareTo(p) == 0; }
    @Override public int hashCode() { return asDecimal().stripTrailingZeros().hashCode(); }
    @Override public String toString() { return asDecimal().stripTrailingZeros().toPlainString(); }
}
