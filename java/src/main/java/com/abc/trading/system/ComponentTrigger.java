package com.abc.trading.system;

/** Lifecycle triggers corresponding to Nautilus ComponentTrigger. */
public enum ComponentTrigger {
    INITIALIZE,
    START,
    START_COMPLETED,
    STOP,
    STOP_COMPLETED,
    RESUME,
    RESUME_COMPLETED,
    RESET,
    RESET_COMPLETED,
    DISPOSE,
    DISPOSE_COMPLETED,
    DEGRADE,
    DEGRADE_COMPLETED,
    FAULT,
    FAULT_COMPLETED
}