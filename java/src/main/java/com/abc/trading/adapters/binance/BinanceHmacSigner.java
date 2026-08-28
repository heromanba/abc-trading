package com.abc.trading.adapters.binance;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;

/** Binance HMAC-SHA256 query signer, matching the signed REST contract. */
public final class BinanceHmacSigner {
    private BinanceHmacSigner() { }

    public static String sign(String secret, String query) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("secret is required");
        if (query == null) throw new IllegalArgumentException("query is required");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
    }

    public static String sign(String secret, Map<String, String> parameters) {
        return sign(secret, BinanceQuery.encode(parameters));
    }
}
