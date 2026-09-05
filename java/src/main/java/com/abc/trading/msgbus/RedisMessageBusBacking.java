package com.abc.trading.msgbus;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Redis Streams transport compatible with the Rust message-bus field contract. */
public final class RedisMessageBusBacking implements MessageBusBacking, AutoCloseable {
    static final String TOPIC_FIELD = "topic";
    static final String TYPE_FIELD = "type";
    static final String PAYLOAD_FIELD = "payload";
    static final String ENCODING_FIELD = "encoding";

    private final RedisMessageBusConfig config;
    private final JedisPooled redis;
    private final ExecutorService consumers;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    public RedisMessageBusBacking() {
        this(RedisMessageBusConfig.defaults());
    }

    public RedisMessageBusBacking(RedisMessageBusConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.redis = new JedisPooled(java.net.URI.create(config.redisUri()));
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "abc-trading-redis-stream");
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

    @Override
    public void publish(BusMessage message) {
        Objects.requireNonNull(message, "message");
        ensureOpen();
        Map<String, String> fields = encode(message);
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                redis.xadd(config.streamKey(), StreamEntryID.NEW_ENTRY, fields);
                return;
            } catch (JedisConnectionException error) {
                lastError = error;
                failure.compareAndSet(null, error);
                if (attempt < config.maxRetries()) sleepBeforeRetry();
            }
        }
        throw lastError;
    }

    /**
     * Starts a consumer-group subscription. A message is acknowledged only after
     * the handler returns successfully; failed messages remain pending for retry.
     */
    public RedisSubscription subscribe(String group, String consumer, Consumer<BusMessage> handler) {
        if (group == null || group.isBlank()) throw new IllegalArgumentException("group is required");
        if (consumer == null || consumer.isBlank()) throw new IllegalArgumentException("consumer is required");
        Objects.requireNonNull(handler, "handler");
        ensureOpen();
        createConsumerGroup(group);
        AtomicBoolean subscriptionClosed = new AtomicBoolean(false);
        Future<?> task = consumers.submit(() -> consume(group, consumer, handler, subscriptionClosed));
        return new RedisSubscription(subscriptionClosed, task);
    }

    /** Convenience subscription that forwards decoded messages to the typed bus. */
    public RedisSubscription subscribe(String group, String consumer, MessageBus bus) {
        Objects.requireNonNull(bus, "bus");
        return subscribe(group, consumer, message -> {
            try {
                bus.publishExternal(message);
            } catch (Exception error) {
                throw new IllegalStateException("Unable to deserialize Redis message", error);
            }
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        consumers.shutdownNow();
        redis.close();
    }

    static Map<String, String> encode(BusMessage message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(TOPIC_FIELD, message.getTopic());
        fields.put(TYPE_FIELD, message.getPayloadType());
        fields.put(PAYLOAD_FIELD, new String(message.getPayload(), StandardCharsets.ISO_8859_1));
        fields.put(ENCODING_FIELD, message.getEncoding().name());
        return fields;
    }

    static BusMessage decode(StreamEntry entry) {
        Map<String, String> fields = entry.getFields();
        String topic = required(fields, TOPIC_FIELD);
        String type = required(fields, TYPE_FIELD);
        String payload = required(fields, PAYLOAD_FIELD);
        SerializationEncoding encoding = SerializationEncoding.valueOf(required(fields, ENCODING_FIELD));
        return new BusMessage(topic, type, payload.getBytes(StandardCharsets.ISO_8859_1), encoding);
    }

    private void createConsumerGroup(String group) {
        try {
            redis.xgroupCreate(config.streamKey(), group, new StreamEntryID("0-0"), true);
        } catch (JedisDataException error) {
            if (!error.getMessage().contains("BUSYGROUP")) throw error;
        }
    }

    private void consume(String group, String consumer, Consumer<BusMessage> handler,
            AtomicBoolean subscriptionClosed) {
        XReadGroupParams params = XReadGroupParams.xReadGroupParams()
                .count(config.batchSize())
            .block(Math.toIntExact(config.blockTimeout().toMillis()));
        boolean readPending = true;
        while (!closed.get() && !subscriptionClosed.get()) {
            try {
                Map<String, List<StreamEntry>> messages = redis.xreadGroupAsMap(
                        group, consumer, params,
                Map.of(config.streamKey(), readPending
                                ? new StreamEntryID("0-0") : StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY));
            if (messages == null || messages.isEmpty()) {
                readPending = false;
                continue;
            }
                for (StreamEntry entry : messages.getOrDefault(config.streamKey(), List.of())) {
                    if (subscriptionClosed.get() || closed.get()) return;
                    handler.accept(decode(entry));
                    redis.xack(config.streamKey(), group, entry.getID());
                }
            readPending = false;
            } catch (JedisConnectionException error) {
                if (closed.get() || subscriptionClosed.get()) return;
                failure.compareAndSet(null, error);
                readPending = true;
                sleepBeforeRetry();
            } catch (RuntimeException error) {
                if (closed.get() || subscriptionClosed.get()) return;
                failure.compareAndSet(null, error);
                readPending = true;
                sleepBeforeRetry();
            }
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(config.retryDelay().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Redis message bus backing is closed");
    }

    private static String required(Map<String, String> fields, String field) {
        String value = fields.get(field);
        if (value == null) throw new IllegalArgumentException("Redis message is missing field: " + field);
        return value;
    }

    public static final class RedisSubscription implements AutoCloseable {
        private final AtomicBoolean closed;
        private final Future<?> task;

        private RedisSubscription(AtomicBoolean closed, Future<?> task) {
            this.closed = closed;
            this.task = task;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) task.cancel(true);
        }
    }
}
