package com.abc.trading.msgbus;

public class BusMessage {
    private final String topic;
    private final String payloadType;
    private final byte[] payload;
    private final SerializationEncoding encoding;

    public BusMessage(String topic, String payloadType, byte[] payload, SerializationEncoding encoding) {
        this.topic = topic;
        this.payloadType = payloadType;
        this.payload = payload;
        this.encoding = encoding;
    }

    public String getTopic() { return topic; }
    public String getPayloadType() { return payloadType; }
    public byte[] getPayload() { return payload; }
    public SerializationEncoding getEncoding() { return encoding; }
}
