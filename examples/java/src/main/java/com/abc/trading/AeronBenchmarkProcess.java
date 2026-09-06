package com.abc.trading;

import com.abc.trading.msgbus.AeronMessageBusBacking;
import com.abc.trading.msgbus.AeronMessageBusConfig;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

/** Child JVM consumer used by the cross-process transport benchmark. */
public final class AeronBenchmarkProcess {
    private AeronBenchmarkProcess() {
    }

    public static void main(String[] args) throws Exception {
        String directory = args[0];
        int streamId = Integer.parseInt(args[1]);
        AeronMessageBusConfig config = new AeronMessageBusConfig(
                "aeron:ipc", streamId, false, directory, 10, 10_000, Duration.ofMillis(1));
        CountDownLatch keepAlive = new CountDownLatch(1);
        try (AeronMessageBusBacking backing = new AeronMessageBusBacking(config);
                AeronMessageBusBacking.AeronSubscription ignored = backing.subscribe(message -> { })) {
            System.out.println("READY");
            System.out.flush();
            keepAlive.await();
        }
    }
}
