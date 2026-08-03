package com.abc.trading.msgbus;

public interface Serializer {
    byte[] serialize(Object obj) throws Exception;
    <T> T deserialize(byte[] data, Class<T> cls) throws Exception;
}
