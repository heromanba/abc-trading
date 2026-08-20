package com.abc.trading.execution;

public enum OrderStatus {
    INITIALIZED,
    EMULATED,
    RELEASED,
    SUBMITTED,
    ACCEPTED,
    PENDING_UPDATE,
    PENDING_CANCEL,
    PARTIALLY_FILLED,
    FILLED,
    DENIED,
    CANCELED,
    EXPIRED,
    REJECTED,
    TRIGGERED,
    VOIDED;

    public boolean isOpen() {
        return this == SUBMITTED || this == ACCEPTED || this == PENDING_UPDATE
                || this == PENDING_CANCEL || this == PARTIALLY_FILLED;
    }

    public boolean isTerminal() {
        return this == FILLED || this == DENIED || this == CANCELED
                || this == EXPIRED || this == REJECTED || this == VOIDED;
    }
}
