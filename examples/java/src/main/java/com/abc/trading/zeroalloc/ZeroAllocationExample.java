package com.abc.trading.zeroalloc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Example of zero-allocation patterns for high-volume message processing.
 *
 * Demonstrates:
 * - Flyweight views over a raw byte buffer.
 * - Object pooling for reusable state objects.
 * - Primitive clustering with a long-to-long hash map.
 */
public final class ZeroAllocationExample {
    private ZeroAllocationExample() {
    }

    public static final class QuoteFlyweight {
        /**
         * Enforces a fixed binary footprint for financial tickers (e.g."EURUSD", "AAPL")
         * within a raw byte buffer. In HFT and ultra-low-latency engineering, this variable
         * serves several critical architecture functions:
         * 
         * 1. Enables deterministic memory offsets
         * Because every symbol is allocated exactly 24 bytes, the exact start position (offset)
         * of every data field inside the buffer can be calculated via simple arithmetic.
         * - Bid price always starts at byte 24.
         * - Ask price always starts at byte 32.
         * - Timestamp always starts at byte 40.  
         * 
         * Without a fixed width, fields would shift unpredictably depending on whether a symbol
         * was 3 characters ("AMD") or 6 characters ("EURUSD"), forcing the code to dynamically 
         * parse data sequentially.
         * 
         * 2. Eliminates object allocation
         * In standard Java, strings vary in size and live on the managed heap as separate objects.
         * By writing raw ASCII bytes directly into a pre-allocated ByteBuffer up to a limit of 24
         * bytes, the program performs string tracking without creating Java objects. This keeps 
         * garbage collection (GC) pauses at zero.
         * 
         * 3. Handling sizing and padding
         * The variable directly governs how data is written and validated in the set and matchesSymbol
         * functions:
         * - Truncation: if a ticker string is accidentally longer than 24 bytes, it is safely truncated
         * to 24 to prevent it from overwriting adjacent numeric data.
         * - Zero-padding: if a ticker string is shorter than 24 bytes (like "AAPL", which is 4 bytes),
         * the code pads the remaining 20 bytes with (byte) 0. This establishes clean boundaries for string
         * retrieval and comparison routines.
         */
        private static final int SYMBOL_BYTES = 24; // fixed-width ASCII symbol
        private static final int BID_OFFSET = SYMBOL_BYTES;
        private static final int ASK_OFFSET = BID_OFFSET + Double.BYTES;
        private static final int TS_OFFSET = ASK_OFFSET + Double.BYTES;
        public static final int SIZE = TS_OFFSET + Long.BYTES;

        private final ByteBuffer buffer;
        private int baseOffset;

        public QuoteFlyweight(ByteBuffer buffer) {
            this.buffer = Objects.requireNonNull(buffer);
        }

        public void wrap(int offset) {
            this.baseOffset = offset;
        }

        public void set(String symbol, double bid, double ask, long ts) {
            byte[] bytes = symbol.getBytes(StandardCharsets.US_ASCII);
            int len = Math.min(bytes.length, SYMBOL_BYTES);
            buffer.position(baseOffset);
            buffer.put(bytes, 0, len);
            for (int i = len; i < SYMBOL_BYTES; i++) {
                buffer.put((byte) 0);
            }
            buffer.putDouble(baseOffset + BID_OFFSET, bid);
            buffer.putDouble(baseOffset + ASK_OFFSET, ask);
            buffer.putLong(baseOffset + TS_OFFSET, ts);
        }

        public CharSequence symbolView() {
            return new AsciiCharSequence(buffer, baseOffset, SYMBOL_BYTES);
        }

        public double bid() {
            return buffer.getDouble(baseOffset + BID_OFFSET);
        }

        public double ask() {
            return buffer.getDouble(baseOffset + ASK_OFFSET);
        }

        public long timestamp() {
            return buffer.getLong(baseOffset + TS_OFFSET);
        }

        public boolean matchesSymbol(CharSequence expected) {
            if (expected.length() > SYMBOL_BYTES) {
                return false;
            }
            for (int i = 0; i < expected.length(); i++) {
                byte b = buffer.get(baseOffset + i);
                if (b == 0 || b != expected.charAt(i)) {
                    return false;
                }
            }
            return buffer.get(baseOffset + expected.length()) == 0;
        }
    }

    public static final class OrderState {
        private long orderId;
        private double filledQuantity;
        private double remainingQuantity;
        private int statusCode;

        public void reset(long orderId, double filledQuantity, double remainingQuantity, int statusCode) {
            this.orderId = orderId;
            this.filledQuantity = filledQuantity;
            this.remainingQuantity = remainingQuantity;
            this.statusCode = statusCode;
        }

        public void clear() {
            this.orderId = 0;
            this.filledQuantity = 0;
            this.remainingQuantity = 0;
            this.statusCode = 0;
        }

        public long orderId() {
            return orderId;
        }

        public double filledQuantity() {
            return filledQuantity;
        }

        public double remainingQuantity() {
            return remainingQuantity;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    public static final class OrderStatePool {
        private final Deque<OrderState> pool;
        private final int maxSize;

        public OrderStatePool(int maxSize) {
            this.pool = new ArrayDeque<>(maxSize);
            this.maxSize = maxSize;
        }

        public OrderState borrow() {
            OrderState state = pool.pollFirst();
            if (state == null) {
                state = new OrderState();
            }
            return state;
        }

        public void release(OrderState state) {
            state.clear();
            if (pool.size() < maxSize) {
                pool.addFirst(state);
            }
        }
    }

    public static final class LongLongHashMap {
        private static final long EMPTY = Long.MIN_VALUE;
        private long[] keys;
        private long[] values;
        private int size;

        public LongLongHashMap(int capacity) {
            int cap = Integer.highestOneBit(capacity - 1) << 1;
            keys = new long[cap];
            values = new long[cap];
            clear();
        }

        public void clear() {
            size = 0;
            for (int i = 0; i < keys.length; i++) {
                keys[i] = EMPTY;
            }
        }

        private int index(long key) {
            int mask = keys.length - 1;
            int idx = Long.hashCode(key) & mask;
            while (keys[idx] != EMPTY && keys[idx] != key) {
                idx = (idx + 1) & mask;
            }
            return idx;
        }

        public void put(long key, long value) {
            int idx = index(key);
            if (keys[idx] == EMPTY) {
                keys[idx] = key;
                values[idx] = value;
                size++;
                if (size * 2 > keys.length) {
                    resize(keys.length << 1);
                }
            } else {
                values[idx] = value;
            }
        }

        public long getOrDefault(long key, long defaultValue) {
            int idx = index(key);
            return keys[idx] == key ? values[idx] : defaultValue;
        }

        public boolean containsKey(long key) {
            int idx = index(key);
            return keys[idx] == key;
        }

        private void resize(int newCapacity) {
            long[] oldKeys = keys;
            long[] oldValues = values;
            keys = new long[newCapacity];
            values = new long[newCapacity];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = EMPTY;
            }
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i] != EMPTY) {
                    put(oldKeys[i], oldValues[i]);
                }
            }
        }
    }

    private static final class AsciiCharSequence implements CharSequence {
        private final ByteBuffer buffer;
        private final int offset;
        private final int length;

        AsciiCharSequence(ByteBuffer buffer, int offset, int length) {
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public int length() {
            int effective = 0;
            for (int i = 0; i < length; i++) {
                if (buffer.get(offset + i) == 0) {
                    break;
                }
                effective++;
            }
            return effective;
        }

        @Override
        public char charAt(int index) {
            return (char) buffer.get(offset + index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            int len = end - start;
            byte[] bytes = new byte[len];
            buffer.position(offset + start);
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        @Override
        public String toString() {
            int len = length();
            byte[] bytes = new byte[len];
            buffer.position(offset);
            buffer.get(bytes, 0, len);
            return new String(bytes, StandardCharsets.US_ASCII);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ByteBuffer storage = ByteBuffer.allocateDirect(QuoteFlyweight.SIZE * 128);
        QuoteFlyweight quote = new QuoteFlyweight(storage);
        OrderStatePool pool = new OrderStatePool(16);
        LongLongHashMap metrics = new LongLongHashMap(64);

        // Flyweight buffer write / reuse
        for (int i = 0; i < 4; i++) {
            quote.wrap(i * QuoteFlyweight.SIZE);
            quote.set("EURUSD", 1.2345 + i * 0.0001, 1.2346 + i * 0.0001, System.currentTimeMillis());
            System.out.println("Quote[" + i + "] symbol=" + quote.symbolView() + " bid=" + quote.bid());
        }

        // Object pooling
        OrderState order = pool.borrow();
        order.reset(123L, 10.0, 90.0, 1);
        System.out.println("Order " + order.orderId() + " filled=" + order.filledQuantity());
        pool.release(order);

        // Primitive clustering
        metrics.put(1001L, 250L);
        metrics.put(1002L, 375L);
        System.out.println("Metric 1001=" + metrics.getOrDefault(1001L, 0L));
        System.out.println("Metric 1003 present=" + metrics.containsKey(1003L));
    }
}
