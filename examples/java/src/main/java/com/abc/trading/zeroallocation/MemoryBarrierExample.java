package com.abc.trading.zeroallocation;

/**
 * Small example showing how a volatile write acts like a memory barrier.
 *
 * <p>In Java, a write to a volatile field has release semantics and a read of
 * that same field has acquire semantics. Together they create a happens-before
 * relationship that prevents the consumer thread from observing the flag as
 * ready while still seeing stale data in the payload.
 *
 * <p>Pattern to remember:
 * writer: payload = value; ready = true;
 * reader: while (!ready) { } ; read payload
 *
 * <p>The volatile write to {@code ready} is the barrier that makes the payload
 * write visible to the reader.
 */
public final class MemoryBarrierExample {

    private MemoryBarrierExample() {
    }

    public static void main(String[] args) throws InterruptedException {
        var mailbox = new VolatileMailbox();

        Thread consumer = new Thread(() -> {
            while (!mailbox.ready) {
                Thread.onSpinWait();
            }
            System.out.println("Consumer saw payload = " + mailbox.payload);
        });

        consumer.start();

        mailbox.payload = 42;
        mailbox.ready = true; // volatile write: acts like a release barrier

        consumer.join();
    }

    /**
     * Safe publication example.
     */
    static final class VolatileMailbox {
        int payload;
        volatile boolean ready;
    }

    /**
     * Unsafe publication example.
     *
     * <p>If {@code ready} were not volatile, the compiler/CPU could reorder
     * or cache reads and writes in a way that lets another thread observe
     * {@code ready == true} but still see an old {@code payload} value.
     */
    static final class PlainMailbox {
        int payload;
        boolean ready;
    }
}