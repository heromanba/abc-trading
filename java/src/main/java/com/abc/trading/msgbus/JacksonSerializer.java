package com.abc.trading.msgbus;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonSerializer implements Serializer {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object obj) throws Exception {
        return mapper.writeValueAsBytes(obj);
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> cls) throws Exception {
        return mapper.readValue(data, cls);
    }
}
