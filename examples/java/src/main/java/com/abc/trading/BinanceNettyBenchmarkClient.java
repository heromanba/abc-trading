package com.abc.trading;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BinanceNettyBenchmarkClient {
    private static final String HOST = "stream.binance.com";
    private static final int PORT = 9443;
    private static final String URL_STRING = "wss://stream.binance.com:9443/ws/btcusdt@bookTicker";
    private static final int BENCHMARK_SECONDS = 45;
    private static final int WARMUP_SECONDS = 5;

    public static void main(String[] args) throws Exception {
        URI uri = new URI(URL_STRING);
        List<String> strategies = List.of("baseline", "pooled", "epoll");
        for (String strategy : strategies) {
            System.out.println("=== strategy: " + strategy + " ===");
            runStrategy(strategy, uri);
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }
    }

    private static void runStrategy(String strategy, URI uri) throws Exception {
        EventLoopGroup group = strategy.equals("epoll") ? new EpollEventLoopGroup(2) : new NioEventLoopGroup(2);
        AtomicInteger messages = new AtomicInteger();
        AtomicInteger parseErrors = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        AtomicLong totalLatencyNanos = new AtomicLong();
        AtomicLong maxLatencyNanos = new AtomicLong();
        AtomicLong firstMessageNanos = new AtomicLong(-1);
        AtomicLong lastSequence = new AtomicLong(-1);
        AtomicLong orderingSkewNanos = new AtomicLong(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        long startNanos = System.nanoTime();

        try {
            SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(strategy.equals("epoll") ? EpollSocketChannel.class : NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, strategy.equals("pooled") ? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(sslCtx.newHandler(ch.alloc(), HOST, PORT));
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(8192 * 1024));
                        p.addLast(new BinanceBenchmarkHandler(uri, messages, parseErrors, disconnects, totalLatencyNanos, maxLatencyNanos, firstMessageNanos, lastSequence, orderingSkewNanos, latencies));
                    }
                });

            long connectStart = System.nanoTime();
            Channel channel = bootstrap.connect(HOST, PORT).sync().channel();
            long connectionSetupNanos = System.nanoTime() - connectStart;
            Thread.sleep(TimeUnit.SECONDS.toMillis(WARMUP_SECONDS * 1000L));
            long warmupStartNanos = System.nanoTime();
            Thread.sleep(TimeUnit.SECONDS.toMillis(BENCHMARK_SECONDS * 1000L));
            long durationSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - warmupStartNanos);
            long count = messages.get();
            long parseErrorsCount = parseErrors.get();
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            int p999Index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.999));
            long p999LatencyNanos = sorted.isEmpty() ? 0 : sorted.get(p999Index);
            double avgLatencyNanos = count == 0 ? 0 : (double) totalLatencyNanos.get() / count;
            long jitterNanos = sorted.isEmpty() ? 0 : sorted.stream().mapToLong(v -> v).max().orElse(0L) - sorted.stream().mapToLong(v -> v).min().orElse(0L);
            double throughputMps = durationSeconds <= 0 ? 0.0 : count / (double) durationSeconds;
            long heapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long gcTimeMillis = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();

            System.out.println("strategy=" + strategy + " duration_seconds=" + BENCHMARK_SECONDS + " warmup_seconds=" + WARMUP_SECONDS);
            System.out.println("  messages=" + count);
            System.out.println("  parse_errors=" + parseErrorsCount);
            System.out.println("  disconnects=" + disconnects.get());
            System.out.println("  connection_setup_ms=" + TimeUnit.NANOSECONDS.toMillis(connectionSetupNanos));
            System.out.println("  time_to_first_message_ms=" + (firstMessageNanos.get() >= 0 ? TimeUnit.NANOSECONDS.toMillis(firstMessageNanos.get()) : -1));
            System.out.println("  average_ingest_latency_us=" + String.format("%.3f", avgLatencyNanos / 1000.0));
            System.out.println("  p99_9_latency_us=" + String.format("%.3f", p999LatencyNanos / 1000.0));
            System.out.println("  jitter_us=" + String.format("%.3f", jitterNanos / 1000.0));
            System.out.println("  throughput_mps=" + String.format("%.3f", throughputMps));
            System.out.println("  ordering_skew_us=" + String.format("%.3f", orderingSkewNanos.get() / 1000.0));
            System.out.println("  heap_bytes=" + heapBytes);
            System.out.println("  gc_time_ms=" + gcTimeMillis);
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    static class BinanceBenchmarkHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private final AtomicInteger messages;
        private final AtomicInteger parseErrors;
        private final AtomicInteger disconnects;
        private final AtomicLong totalLatencyNanos;
        private final AtomicLong maxLatencyNanos;
        private final AtomicLong firstMessageNanos;
        private final AtomicLong lastSequence;
        private final AtomicLong orderingSkewNanos;
        private final List<Long> latencies;
        private final long startNanos;

        BinanceBenchmarkHandler(URI uri, AtomicInteger messages, AtomicInteger parseErrors, AtomicInteger disconnects, AtomicLong totalLatencyNanos, AtomicLong maxLatencyNanos, AtomicLong firstMessageNanos, AtomicLong lastSequence, AtomicLong orderingSkewNanos, List<Long> latencies) {
            this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
            this.messages = messages;
            this.parseErrors = parseErrors;
            this.disconnects = disconnects;
            this.totalLatencyNanos = totalLatencyNanos;
            this.maxLatencyNanos = maxLatencyNanos;
            this.firstMessageNanos = firstMessageNanos;
            this.lastSequence = lastSequence;
            this.orderingSkewNanos = orderingSkewNanos;
            this.latencies = latencies;
            this.startNanos = System.nanoTime();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            disconnects.incrementAndGet();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                return;
            }
            if (msg instanceof TextWebSocketFrame frame) {
                String text = frame.text();
                long parseStart = System.nanoTime();
                List<TickerMessage> payloads = parsePayload(text);
                long latencyNanos = System.nanoTime() - parseStart;
                if (!payloads.isEmpty()) {
                    int count = messages.addAndGet(payloads.size());
                    totalLatencyNanos.addAndGet(latencyNanos * payloads.size());
                    maxLatencyNanos.accumulateAndGet(latencyNanos, Math::max);
                    for (int i = 0; i < payloads.size(); i++) {
                        latencies.add(latencyNanos);
                    }
                    if (firstMessageNanos.get() < 0) {
                        firstMessageNanos.set(System.nanoTime() - startNanos);
                    }
                    for (TickerMessage payload : payloads) {
                        long seq = payload.eventTime();
                        long previous = lastSequence.getAndSet(seq);
                        if (previous >= 0 && seq < previous) {
                            orderingSkewNanos.addAndGet(previous - seq);
                        }
                    }
                    if (count % 100 == 0) {
                        System.out.println("netty processed " + count + " messages");
                    }
                } else {
                    parseErrors.incrementAndGet();
                }
            }
        }

        private List<TickerMessage> parsePayload(String text) {
            try {
                Object parsed = new JSONTokener(text).nextValue();
                if (parsed instanceof JSONArray array) {
                    List<TickerMessage> payloads = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        Object item = array.get(i);
                        if (item instanceof JSONObject obj) {
                            payloads.add(new TickerMessage(
                                obj.optString("s", ""),
                                obj.optString("c", ""),
                                obj.optString("v", ""),
                                obj.optLong("E", 0L)
                            ));
                        }
                    }
                    return payloads;
                }
                if (parsed instanceof JSONObject root) {
                    Object data = root.opt("data");
                    if (data instanceof JSONArray array) {
                        List<TickerMessage> payloads = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            Object item = array.get(i);
                            if (item instanceof JSONObject obj) {
                                payloads.add(new TickerMessage(
                                    obj.optString("s", ""),
                                    obj.optString("c", ""),
                                    obj.optString("v", ""),
                                    obj.optLong("E", 0L)
                                ));
                            }
                        }
                        return payloads;
                    }
                    return List.of(new TickerMessage(
                        root.optString("s", ""),
                        root.optString("c", ""),
                        root.optString("v", ""),
                        root.optLong("E", 0L)
                    ));
                }
                return List.of();
            } catch (Exception ex) {
                return List.of();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    private record TickerMessage(String symbol, String closePrice, String volume, long eventTime) {}
}
