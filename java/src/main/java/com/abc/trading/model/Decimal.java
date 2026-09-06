package com.abc.trading.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/** Exact decimal value used at Nautilus model boundaries. */
public final class Decimal implements Comparable<Decimal> {
    public static final MathContext CONTEXT = MathContext.DECIMAL128;
    private final BigDecimal value;

    public Decimal(BigDecimal value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public Decimal(String value) {
        this(new BigDecimal(value));
    }

    public static Decimal zero() { return new Decimal(BigDecimal.ZERO); }
    public static Decimal of(long value) { return new Decimal(BigDecimal.valueOf(value)); }
    public static Decimal of(double value) { return new Decimal(BigDecimal.valueOf(value)); }
    public BigDecimal asBigDecimal() { return value; }
    public String asString() { return value.toPlainString(); }
    public double asDouble() { return value.doubleValue(); }
    public Decimal add(Decimal other) { return new Decimal(value.add(other.value, CONTEXT)); }
    public Decimal subtract(Decimal other) { return new Decimal(value.subtract(other.value, CONTEXT)); }
    public Decimal multiply(Decimal other) { return new Decimal(value.multiply(other.value, CONTEXT)); }
    public Decimal divide(Decimal other) { return new Decimal(value.divide(other.value, CONTEXT)); }
    public Decimal negate() { return new Decimal(value.negate(CONTEXT)); }
    public int signum() { return value.signum(); }

    @Override public int compareTo(Decimal other) { return value.compareTo(other.value); }
    @Override public boolean equals(Object other) {
        return other instanceof Decimal decimal && value.compareTo(decimal.value) == 0;
    }
    @Override public int hashCode() { return value.stripTrailingZeros().hashCode(); }
    @Override public String toString() { return asString(); }
}
