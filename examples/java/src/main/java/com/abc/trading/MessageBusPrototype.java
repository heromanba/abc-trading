package com.abc.trading;

import com.abc.trading.msgbus.*;
import com.abc.trading.msgbus.types.QuoteTick;

import java.util.UUID;

public class MessageBusPrototype {
    public static void main(String[] args) throws Exception {
        JacksonSerializer serializer = new JacksonSerializer();
        MessageBus bus = new MessageBus(serializer);

        // register canonical name used by external messages
        bus.registerType("QuoteTick", QuoteTick.class);

        // subscribe typed handler
        bus.subscribe(QuoteTick.class, q -> System.out.println("Received typed: " + q));

        // publish typed directly (zero-cost dispatch)
        bus.publish(new QuoteTick("ETHUSDT", 12.3, 12.4, System.currentTimeMillis()));

        // publish external message (serialized payload)
        QuoteTick q = new QuoteTick("BTCUSDT", 100.0, 101.0, System.currentTimeMillis());
        byte[] payload = serializer.serialize(q);
        BusMessage bm = new BusMessage("market/quote", "QuoteTick", payload, SerializationEncoding.JSON);
        bus.publishExternal(bm);

        // request/response example
        UUID id = bus.request(new Object(), res -> System.out.println("Got response: " + res));
        bus.response(id, "ok");
    }
}
