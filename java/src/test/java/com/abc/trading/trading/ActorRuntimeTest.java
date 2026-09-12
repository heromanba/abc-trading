package com.abc.trading.trading;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorRuntimeTest {
    @Test
    void deliversMailboxAndTimersInDeterministicOrder() {
        List<String> received = new ArrayList<>();
        try (ActorRuntime<String> runtime = new ActorRuntime<>("timer-actor", new ActorRuntime.ActorLifecycle<>() {
            @Override public void onMessage(String message, ActorRuntime<String> ignored) { received.add(message); }
        }, 4)) {

        runtime.start(100);
        runtime.schedule(200, "timer-200");
        runtime.schedule(150, "timer-150");
        runtime.send("immediate");
        runtime.advanceTo(200);

        assertEquals(List.of("immediate", "timer-150", "timer-200"), received);
        }
    }

    @Test
    void boundsMailboxAndEmitsOverflow() {
        List<ActorEvent> events = new ArrayList<>();
        try (ActorRuntime<String> runtime = new ActorRuntime<>("bounded", new ActorRuntime.ActorLifecycle<>() {
            @Override public void onMessage(String message, ActorRuntime<String> ignored) {
                if (message.equals("hold")) {
                    ignored.scheduleAfter(0, "queued-1");
                    ignored.scheduleAfter(0, "queued-2");
                }
            }
        }, 1, events::add)) {
        runtime.start(0);
        assertTrue(runtime.send("hold"));
        assertEquals(0, runtime.mailboxSize());
        assertTrue(events.stream().anyMatch(event -> event.type() == ActorEventType.MAILBOX_OVERFLOW));
        }
    }

    @Test
    void faultsStopProcessingAndRestartResetsFault() {
        List<String> received = new ArrayList<>();
        try (ActorRuntime<String> runtime = new ActorRuntime<>("faulty", new ActorRuntime.ActorLifecycle<>() {
            @Override public void onMessage(String message, ActorRuntime<String> ignored) {
                if (message.equals("fail")) throw new IllegalStateException("boom");
                received.add(message);
            }
        }, 4)) {
        runtime.start(0);
        runtime.send("fail");
        assertTrue(runtime.isFaulted());
        assertFalse(runtime.isRunning());
        runtime.restart(100);
        assertTrue(runtime.send("recovered"));
        assertEquals(List.of("recovered"), received);
        assertTrue(runtime.events().stream().anyMatch(event -> event.type() == ActorEventType.FAULTED));
        assertTrue(runtime.events().stream().anyMatch(event -> event.type() == ActorEventType.RESTARTED));
        }
    }
}
