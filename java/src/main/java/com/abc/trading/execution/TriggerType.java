package com.abc.trading.execution;

/** Trigger price source aligned with Nautilus TriggerType. */
public enum TriggerType {
    NO_TRIGGER,
    DEFAULT,
    LAST_PRICE,
    MARK_PRICE,
    INDEX_PRICE,
    BID_ASK,
    DOUBLE_LAST,
    DOUBLE_BID_ASK,
    LAST_OR_BID_ASK,
    MID_POINT
}