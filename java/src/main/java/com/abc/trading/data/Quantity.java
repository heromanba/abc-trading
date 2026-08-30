package com.abc.trading.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Fixed-point non-negative quantity aligned with Nautilus Quantity semantics. */
public final class Quantity implements Comparable<Quantity> {
    private final long raw;
    private final int precision;

    private Quantity(long raw, int precision) {
        if (raw < 0) throw new IllegalArgumentException("quantity must be non-negative");
        if (precision < 0 || precision > 18) throw new IllegalArgumentException("precision must be in 0..18");
        this.raw = raw;
        this.precision = precision;
    }

    public static Quantity fromRaw(long raw, int precision) {
        return new Quantity(raw, precision);
    }

    public static Quantity fromInt(int value) {
        return fromRaw(value, 0);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Quantity fromJson(BigDecimal value) {
        return fromDecimal(value, Math.max(0, value.scale()));
    }

    public static Quantity fromDecimal(BigDecimal value, int precision) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) throw new IllegalArgumentException("quantity must be non-negative");
        try {
            BigDecimal scaled = value.setScale(precision, RoundingMode.UNNECESSARY).movePointRight(precision);
            return fromRaw(scaled.longValueExact(), precision);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("quantity is not representable at precision " + precision, error);
        }
    }

    public static Quantity fromString(String value, int precision) {
        return fromDecimal(new BigDecimal(value), precision);
    }

    public long raw() {
        return raw;
    }

    public int precision() {
        return precision;
    }

    @JsonValue
    public BigDecimal asDecimal() {
        return BigDecimal.valueOf(raw, precision);
    }

    public double asDouble() {
        return asDecimal().doubleValue();
    }

    public int toIntExact() {
        try {
            return asDecimal().intValueExact();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("quantity cannot be represented as an integer", error);
        }
    }

    public boolean isZero() {
        return raw == 0;
    }

    public Quantity add(Quantity other) {
        Objects.requireNonNull(other, "other");
        int targetPrecision = Math.max(precision, other.precision);
        return fromDecimal(asDecimal().add(other.asDecimal()), targetPrecision);
    }

    public Quantity subtract(Quantity other) {
        Objects.requireNonNull(other, "other");
        int targetPrecision = Math.max(precision, other.precision);
        return fromDecimal(asDecimal().subtract(other.asDecimal()), targetPrecision);
    }

    public Quantity min(Quantity other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public Quantity max(Quantity other) {
        return compareTo(other) >= 0 ? this : other;
    }

    private static long scaleFactor(int precision) {
        return switch (precision) {
            case 0 -> 1L;
            case 1 -> 10L;
            case 2 -> 100L;
            case 3 -> 1_000L;
            case 4 -> 10_000L;
            case 5 -> 100_000L;
            case 6 -> 1_000_000L;
            case 7 -> 10_000_000L;
            case 8 -> 100_000_000L;
            case 9 -> 1_000_000_000L;
            case 10 -> 10_000_000_000L;
            case 11 -> 100_000_000_000L;
            case 12 -> 1_000_000_000_000L;
            case 13 -> 10_000_000_000_000L;
            case 14 -> 100_000_000_000_000L;
            case 15 -> 1_000_000_000_000_000L;
            case 16 -> 10_000_000_000_000_000L;
            case 17 -> 100_000_000_000_000_000L;
            case 18 -> 1_000_000_000_000_000_000L;
            default -> throw new IllegalArgumentException("precision must be in 0..18");
        };
    }

    @Override
    public int compareTo(Quantity other) {
        return asDecimal().compareTo(other.asDecimal());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Quantity quantity && compareTo(quantity) == 0;
    }

    @Override
    public int hashCode() {
        return asDecimal().stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return asDecimal().stripTrailingZeros().toPlainString();
    }
}
