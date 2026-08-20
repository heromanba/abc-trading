package com.abc.trading.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStateMachineTest {
    @Test
    void tracksPartialAndMultipleFillsDeterministically() {
        OrderStateMachine machine = new OrderStateMachine();
        machine.initialize("order-1", 10, TimeInForce.GTC, 0L);
        machine.submit("order-1");
        machine.accept("order-1");

        OrderState partial = machine.fill("order-1", 4, 100.0);
        assertEquals(OrderStatus.PARTIALLY_FILLED, partial.status());
        assertEquals(4, partial.filledQuantity());
        assertEquals(6, partial.remainingQuantity());

        OrderState complete = machine.fill("order-1", 6, 102.0);
        assertEquals(OrderStatus.FILLED, complete.status());
        assertEquals(10, complete.filledQuantity());
        assertEquals(0, complete.remainingQuantity());
        assertEquals(101.2, complete.averageFillPrice(), 1e-9);
    }

    @Test
    void rejectsInvalidTerminalTransitions() {
        OrderStateMachine machine = new OrderStateMachine();
        machine.initialize("order-1", 10, TimeInForce.GTC, 0L);
        machine.submit("order-1");
        machine.accept("order-1");
        machine.fill("order-1", 10, 100.0);

        assertThrows(IllegalStateException.class, () -> machine.cancel("order-1"));
        assertThrows(IllegalArgumentException.class, () -> machine.fill("order-1", 1, 100.0));
    }

    @Test
    void supportsEmulationReleaseTriggerAndVoidCorrection() {
        OrderStateMachine machine = new OrderStateMachine();
        machine.initialize("order-2", 10, TimeInForce.GTC, 0L);

        assertEquals(OrderStatus.EMULATED, machine.emulate("order-2").status());
        assertEquals(OrderStatus.RELEASED, machine.release("order-2").status());
        assertEquals(OrderStatus.SUBMITTED, machine.submit("order-2").status());
        assertEquals(OrderStatus.ACCEPTED, machine.accept("order-2").status());
        assertEquals(OrderStatus.TRIGGERED, machine.trigger("order-2").status());
        assertEquals(OrderStatus.ACCEPTED, machine.accept("order-2").status());
        assertEquals(OrderStatus.PARTIALLY_FILLED, machine.fill("order-2", 4, 100.0).status());
        assertEquals(OrderStatus.VOIDED, machine.voidOrder("order-2").status());

        assertThrows(IllegalStateException.class, () -> machine.trigger("order-2"));
        assertThrows(IllegalStateException.class, () -> machine.voidOrder("order-2"));
    }
}