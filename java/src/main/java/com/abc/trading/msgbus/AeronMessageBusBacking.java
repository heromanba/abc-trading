package com.abc.trading.msgbus;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Ordered Aeron IPC transport with bounded publication retry and one consumer thread. */
public final class AeronMessageBusBacking implements MessageBusBacking, AutoCloseable {
    private static final int VERSION = 1;

    private final AeronMessageBusConfig config;
    private final MediaDriver driver;
    private final Aeron aeron;
    private final Publication publication;
    private final ExecutorService consumers;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final ThreadLocal<PublicationScratch> publicationScratch =
            ThreadLocal.withInitial(PublicationScratch::new);

    public AeronMessageBusBacking() {
        this(AeronMessageBusConfig.embedded());
    }

    public AeronMessageBusBacking(AeronMessageBusConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.driver = config.embeddedDriver()
                ? MediaDriver.launchEmbedded(new MediaDriver.Context()
                        .dirDeleteOnStart(true)
                        .dirDeleteOnShutdown(true))
                : null;
        String directory = config.embeddedDriver() ? driver.aeronDirectoryName() : config.aeronDirectory();
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(directory));
        this.publication = aeron.addPublication(config.channel(), config.streamId());
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "abc-trading-aeron-subscription");
            thread.setDaemon(true);
            return thread;
        };
        this.consumers = Executors.newCachedThreadPool(factory);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public Throwable failure() {
        return failure.get();
    }

    public boolean isConnected() {
        return publication.isConnected();
    }

    public String aeronDirectoryName() {
        return aeron.context().aeronDirectoryName();
    }

    @Override
    public void publish(BusMessage message) {
        Objects.requireNonNull(message, "message");
        ensureOpen();
        PublicationScratch scratch = publicationScratch.get();
        int encodedLength = scratch.encode(message);
        long result = Publication.NOT_CONNECTED;
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            result = publication.offer(scratch.buffer, 0, encodedLength);
            if (result > 0) return;
            if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) break;
            if (attempt < config.maxRetries()) waitBeforeRetry();
        }
        throw new IllegalStateException("Aeron publication failed: " + result);
    }

    public AeronSubscription subscribe(Consumer<BusMessage> handler) {
        Objects.requireNonNull(handler, "handler");
        ensureOpen();
        Subscription subscription = aeron.addSubscription(config.channel(), config.streamId());
        AtomicBoolean subscriptionClosed = new AtomicBoolean(false);
        Future<?> task = consumers.submit(() -> consume(subscription, handler, subscriptionClosed));
        return new AeronSubscription(subscription, subscriptionClosed, task);
    }

    public AeronSubscription subscribe(MessageBus bus) {
        Objects.requireNonNull(bus, "bus");
        return subscribe(message -> {
            try {
                bus.publishExternal(message);
            } catch (Exception error) {
                throw new IllegalStateException("Unable to deserialize Aeron message", error);
            }
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        consumers.shutdownNow();
        publication.close();
        aeron.close();
        if (driver != null) driver.close();
    }

    static byte[] encode(BusMessage message) {
        PublicationScratch scratch = new PublicationScratch();
        return Arrays.copyOf(scratch.storage, scratch.encode(message));
    }

    static BusMessage decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.remaining() < 8 || buffer.getInt() != VERSION) {
            throw new IllegalArgumentException("Unsupported Aeron message envelope");
        }
        String topic = readString(buffer, "topic");
        String type = readString(buffer, "payload type");
        String encoding = readString(buffer, "encoding");
        int payloadLength = readLength(buffer, "payload");
        if (payloadLength != buffer.remaining()) throw new IllegalArgumentException("Invalid Aeron payload length");
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new BusMessage(topic, type, payload, SerializationEncoding.valueOf(encoding));
    }

    private void consume(Subscription subscription, Consumer<BusMessage> handler,
            AtomicBoolean subscriptionClosed) {
        FragmentAssembler assembler = new FragmentAssembler((buffer, offset, length, header) -> {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);
            handler.accept(decode(bytes));
        });
        try {
            while (!closed.get() && !subscriptionClosed.get()) {
                int fragments = subscription.poll(assembler, config.fragmentLimit());
                if (fragments == 0) Thread.yield();
            }
        } catch (Throwable error) {
            if (!closed.get() && !subscriptionClosed.get()) failure.compareAndSet(null, error);
        } finally {
            subscription.close();
        }
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(config.retryDelay().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Aeron message bus backing is closed");
    }

    private static String readString(ByteBuffer buffer, String name) {
        int length = readLength(buffer, name);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readLength(ByteBuffer buffer, String name) {
        if (buffer.remaining() < 4) throw new IllegalArgumentException("Missing Aeron " + name + " length");
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid Aeron " + name + " length");
        }
        return length;
    }

    private static final class PublicationScratch {
        private byte[] storage = new byte[1024];
        private UnsafeBuffer buffer = new UnsafeBuffer(storage);
        private String topic;
        private String payloadType;
        private SerializationEncoding encoding;
        private byte[] prefix;

        private int encode(BusMessage message) {
            if (!message.getTopic().equals(topic)
                    || !message.getPayloadType().equals(payloadType)
                    || message.getEncoding() != encoding) {
                topic = message.getTopic();
                payloadType = message.getPayloadType();
                encoding = message.getEncoding();
                prefix = encodePrefix(message);
            }

            byte[] payload = message.getPayload();
            int totalLength = prefix.length + Integer.BYTES + payload.length;
            ensureCapacity(totalLength);
            System.arraycopy(prefix, 0, storage, 0, prefix.length);
            buffer.putInt(prefix.length, payload.length, ByteOrder.BIG_ENDIAN);
            buffer.putBytes(prefix.length + Integer.BYTES, payload);
            return totalLength;
        }

        private void ensureCapacity(int requiredLength) {
            if (requiredLength <= storage.length) return;
            int capacity = storage.length;
            while (capacity < requiredLength) capacity = Math.multiplyExact(capacity, 2);
            storage = Arrays.copyOf(storage, capacity);
            buffer.wrap(storage);
        }

        private static byte[] encodePrefix(BusMessage message) {
            byte[] topic = message.getTopic().getBytes(StandardCharsets.UTF_8);
            byte[] type = message.getPayloadType().getBytes(StandardCharsets.UTF_8);
            byte[] encoding = message.getEncoding().name().getBytes(StandardCharsets.US_ASCII);
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + topic.length + 4 + type.length
                    + 4 + encoding.length).order(ByteOrder.BIG_ENDIAN);
            buffer.putInt(VERSION).putInt(topic.length).put(topic).putInt(type.length).put(type)
                    .putInt(encoding.length).put(encoding);
            return buffer.array();
        }
    }

    public static final class AeronSubscription implements AutoCloseable {
        private final Subscription subscription;
        private final AtomicBoolean closed;
        private final Future<?> task;

        private AeronSubscription(Subscription subscription, AtomicBoolean closed, Future<?> task) {
            this.subscription = subscription;
            this.closed = closed;
            this.task = task;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                subscription.close();
                task.cancel(true);
            }
        }
    }
}
