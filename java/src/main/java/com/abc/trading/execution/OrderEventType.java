package com.abc.trading.execution;

/** Canonical order lifecycle event kinds used for reconciliation and replay. */
public enum OrderEventType {
    INITIALIZED,
    SUBMITTED,
    EMULATED,
    RELEASED,
    ACCEPTED,
    TRIGGERED,
    DENIED,
    REJECTED,
    PENDING_CANCEL,
    CANCELED,
    CANCEL_REJECTED,
    PENDING_UPDATE,
    UPDATED,
    UPDATE_REJECTED,
    PARTIALLY_FILLED,
    FILLED,
    EXPIRED,
    VOIDED
}
