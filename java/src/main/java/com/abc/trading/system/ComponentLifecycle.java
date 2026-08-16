package com.abc.trading.system;

/** Shared state-transition table for lifecycle-managed Java components. */
public final class ComponentLifecycle {
    private ComponentState state = ComponentState.PRE_INITIALIZED;

    public ComponentState state() {
        return state;
    }

    public void initialize() {
        transition(ComponentTrigger.INITIALIZE);
    }

    public void start() {
        transition(ComponentTrigger.START);
    }

    public void startCompleted() {
        transition(ComponentTrigger.START_COMPLETED);
    }

    public void stop() {
        transition(ComponentTrigger.STOP);
    }

    public void stopCompleted() {
        transition(ComponentTrigger.STOP_COMPLETED);
    }

    public void reset() {
        transition(ComponentTrigger.RESET);
    }

    public void resetCompleted() {
        transition(ComponentTrigger.RESET_COMPLETED);
    }

    public void dispose() {
        transition(ComponentTrigger.DISPOSE);
    }

    public void disposeCompleted() {
        transition(ComponentTrigger.DISPOSE_COMPLETED);
    }

    public void transition(ComponentTrigger trigger) {
        if (trigger == null) throw new IllegalArgumentException("trigger is required");
        state = nextState(state, trigger);
    }

    private static ComponentState nextState(ComponentState state, ComponentTrigger trigger) {
        return switch (state) {
            case PRE_INITIALIZED -> require(trigger, ComponentTrigger.INITIALIZE, ComponentState.READY);
            case READY -> switch (trigger) {
                case START -> ComponentState.STARTING;
                case RESET -> ComponentState.RESETTING;
                case DISPOSE -> ComponentState.DISPOSING;
                default -> invalid(state, trigger);
            };
            case STARTING -> switch (trigger) {
                case START_COMPLETED -> ComponentState.RUNNING;
                case STOP -> ComponentState.STOPPING;
                case FAULT -> ComponentState.FAULTING;
                default -> invalid(state, trigger);
            };
            case RUNNING -> switch (trigger) {
                case STOP -> ComponentState.STOPPING;
                case DEGRADE -> ComponentState.DEGRADING;
                case FAULT -> ComponentState.FAULTING;
                default -> invalid(state, trigger);
            };
            case STOPPING -> switch (trigger) {
                case STOP_COMPLETED -> ComponentState.STOPPED;
                case FAULT -> ComponentState.FAULTING;
                default -> invalid(state, trigger);
            };
            case STOPPED -> switch (trigger) {
                case RESET -> ComponentState.RESETTING;
                case RESUME -> ComponentState.RESUMING;
                case DISPOSE -> ComponentState.DISPOSING;
                case FAULT -> ComponentState.FAULTING;
                default -> invalid(state, trigger);
            };
            case RESUMING -> switch (trigger) {
                case RESUME_COMPLETED -> ComponentState.RUNNING;
                case STOP -> ComponentState.STOPPING;
                case FAULT -> ComponentState.FAULTING;
                default -> invalid(state, trigger);
            };
            case RESETTING -> require(trigger, ComponentTrigger.RESET_COMPLETED, ComponentState.READY);
            case DISPOSING -> require(trigger, ComponentTrigger.DISPOSE_COMPLETED, ComponentState.DISPOSED);
            case DEGRADING -> require(trigger, ComponentTrigger.DEGRADE_COMPLETED, ComponentState.DEGRADED);
            case DEGRADED -> switch (trigger) {
                case RESUME -> ComponentState.RESUMING;
                case STOP -> ComponentState.STOPPING;
                case FAULT -> ComponentState.FAULTING;
                default -> invalid(state, trigger);
            };
            case FAULTING -> require(trigger, ComponentTrigger.FAULT_COMPLETED, ComponentState.FAULTED);
            case DISPOSED, FAULTED -> invalid(state, trigger);
        };
    }

    private static ComponentState require(
            ComponentTrigger actual,
            ComponentTrigger expected,
            ComponentState next) {
        return actual == expected ? next : invalid(null, actual);
    }

    private static ComponentState invalid(ComponentState state, ComponentTrigger trigger) {
        throw new IllegalStateException("Invalid lifecycle transition: " + state + " + " + trigger);
    }
}