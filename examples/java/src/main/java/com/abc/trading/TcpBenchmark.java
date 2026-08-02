package com.abc.trading;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple TCP ping-pong benchmark (single connection, client sends longs, server echoes).
 * Measures round-trip throughput and average RTT per message.
 *
 * Usage: run main; the server and client run in the same JVM in separate threads.
 */
public class TcpBenchmark {
    static final int PORT = 54321;
    static final int MESSAGE_COUNT = 1_000_000;
    static final int WARMUP = 1_000;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting TCP benchmark: messages=" + MESSAGE_COUNT);

        Thread serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT, 50, java.net.InetAddress.getByName("127.0.0.1"))) {
                try (Socket s = ss.accept();
                     DataInputStream in = new DataInputStream(s.getInputStream());
                     DataOutputStream out = new DataOutputStream(s.getOutputStream())) {
                    int total = WARMUP + MESSAGE_COUNT;
                    for (int i = 0; i < total; i++) {
                        long v = in.readLong();
                        out.writeLong(v);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "tcp-server");

        serverThread.start();

        // Give server a moment
        Thread.sleep(100);

        long totalRttNanos = 0L;
        try (Socket client = new Socket("127.0.0.1", PORT);
             DataOutputStream out = new DataOutputStream(client.getOutputStream());
             DataInputStream in = new DataInputStream(client.getInputStream())) {

            // Warmup
            for (int i = 0; i < 1000; i++) {
                out.writeLong(i);
                out.flush();
                in.readLong();
            }

            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                long t0 = System.nanoTime();
                out.writeLong(i);
                out.flush();
                long echoed = in.readLong();
                long t1 = System.nanoTime();
                totalRttNanos += (t1 - t0);
                if (echoed != i) {
                    throw new IllegalStateException("mismatch");
                }
            }
            long end = System.nanoTime();

            double totalMs = (end - start) / 1_000_000.0;
            double opsPerSec = MESSAGE_COUNT / ((end - start) / 1_000_000_000.0);
            double avgRttMicros = (totalRttNanos / (double) MESSAGE_COUNT) / 1_000.0;

            System.out.printf("TCP total time: %.3f ms, throughput=%.3f ops/sec, avg RTT=%.3f us\n", totalMs, opsPerSec, avgRttMicros);
        }

        serverThread.join();
    }

    /*
     * Benchmark results appended here by automated run.
     *
     * Run: 2026-08-02
     * Output:
     * Starting TCP benchmark: messages=1000000
     * TCP total time: 20376.391 ms, throughput=49076.405 ops/sec, avg RTT=20.350 us
     */
}
