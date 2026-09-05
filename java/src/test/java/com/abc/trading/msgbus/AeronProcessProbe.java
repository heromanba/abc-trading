package com.abc.trading.msgbus;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-only child process for validating Aeron shared-directory IPC. */
public final class AeronProcessProbe {
    private AeronProcessProbe() {
    }

    public static void main(String[] args) throws Exception {
        String directory = args[0];
        int streamId = Integer.parseInt(args[1]);
        AeronMessageBusConfig config = new AeronMessageBusConfig(
                "aeron:ipc", streamId, false, directory, 10, 10_000, Duration.ofMillis(1));
        CountDownLatch received = new CountDownLatch(1);
        try (AeronMessageBusBacking backing = new AeronMessageBusBacking(config);
                AeronMessageBusBacking.AeronSubscription ignored = backing.subscribe(message -> {
                    System.out.println("RECEIVED:" + message.getTopic() + ":"
                            + new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8));
                    System.out.flush();
                    received.countDown();
                })) {
            System.out.println("READY");
            System.out.flush();
            if (!received.await(10, TimeUnit.SECONDS)) System.exit(2);
        }
    }
}
