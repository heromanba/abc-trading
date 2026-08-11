package com.abc.trading.execution;

public final class DeterministicOrderId {
    private DeterministicOrderId() {
    }

    public static String fromCorrelation(String correlationId) {
        return "ORD-" + correlationId;
    }
}
