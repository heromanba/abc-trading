package com.abc.trading.system;

/** Lifecycle states corresponding to Nautilus component states. */
public enum ComponentState {
    PRE_INITIALIZED,
    READY,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    RESUMING,
    RESETTING,
    DISPOSING,
    DISPOSED,
    DEGRADING,
    DEGRADED,
    FAULTING,
    FAULTED
}