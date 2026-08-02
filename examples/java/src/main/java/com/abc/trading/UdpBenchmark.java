package com.abc.trading;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Simple UDP ping-pong benchmark (single client/server in same JVM).
 * Client sends an 8-byte long, server echoes it back. Measures RTT and throughput.
 */
public class UdpBenchmark {
    static final int PORT = 54322;
    static final int MESSAGE_COUNT = 1_000_000;
    static final int WARMUP = 1_000;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting UDP benchmark: messages=" + MESSAGE_COUNT);

        Thread server = new Thread(() -> {
            try (DatagramSocket ds = new DatagramSocket(PORT, InetAddress.getByName("127.0.0.1"))) {
                int total = WARMUP + MESSAGE_COUNT;
                byte[] buf = new byte[8];
                for (int i = 0; i < total; i++) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    ds.receive(p);
                    // echo back
                    DatagramPacket reply = new DatagramPacket(p.getData(), p.getLength(), p.getAddress(), p.getPort());
                    ds.send(reply);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "udp-server");

        server.start();
        Thread.sleep(100);

        try (DatagramSocket client = new DatagramSocket()) {
            client.connect(InetAddress.getByName("127.0.0.1"), PORT);
            byte[] sendBuf = new byte[8];
            byte[] recvBuf = new byte[8];

            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                ByteBuffer.wrap(sendBuf).putLong(i);
                DatagramPacket sp = new DatagramPacket(sendBuf, sendBuf.length);
                client.send(sp);
                DatagramPacket rp = new DatagramPacket(recvBuf, recvBuf.length);
                client.receive(rp);
            }

            long totalRttNanos = 0L;
            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                ByteBuffer.wrap(sendBuf).putLong(i);
                DatagramPacket sp = new DatagramPacket(sendBuf, sendBuf.length);
                long t0 = System.nanoTime();
                client.send(sp);
                DatagramPacket rp = new DatagramPacket(recvBuf, recvBuf.length);
                client.receive(rp);
                long t1 = System.nanoTime();
                totalRttNanos += (t1 - t0);
            }
            long end = System.nanoTime();

            double totalMs = (end - start) / 1_000_000.0;
            double opsPerSec = MESSAGE_COUNT / ((end - start) / 1_000_000_000.0);
            double avgRttMicros = (totalRttNanos / (double) MESSAGE_COUNT) / 1_000.0;

            System.out.printf("UDP total time: %.3f ms, throughput=%.3f ops/sec, avg RTT=%.3f us\n", totalMs, opsPerSec, avgRttMicros);
        }

        server.join();
    }

    /*
     * Benchmark results appended here by automated run.
     *
     * Run: 2026-08-02
     * Output:
     * Starting UDP benchmark: messages=1000000
     * UDP total time: 18197.970 ms, throughput=54951.183 ops/sec, avg RTT=18.105 us
     */
}
