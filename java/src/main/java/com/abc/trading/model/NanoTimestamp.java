package com.abc.trading.model;

/** Non-negative Unix nanosecond timestamp with explicit event-time ordering. */
public record NanoTimestamp(long nanoseconds) implements Comparable<NanoTimestamp> {
    public NanoTimestamp {
        if (nanoseconds < 0) throw new IllegalArgumentException("nanoseconds must be non-negative");
    }

    public static NanoTimestamp of(long nanoseconds) { return new NanoTimestamp(nanoseconds); }
    public NanoTimestamp plus(long delta) { return new NanoTimestamp(Math.addExact(nanoseconds, delta)); }
    @Override public int compareTo(NanoTimestamp other) { return Long.compare(nanoseconds, other.nanoseconds); }
}
