package com.hoangcoder.vietsms.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Stateless HMAC-SHA256 signer.
 * Returns "sha256=" + lowercase-hex(HMAC-SHA256(body, secret)).
 */
@Component
public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Signs the given body bytes with the provided secret.
     *
     * @param body   raw request body bytes
     * @param secret webhook endpoint secret (UTF-8)
     * @return signature string in format "sha256=<lowercase-hex>"
     * @throws IllegalStateException if the JVM does not support HmacSHA256 (should never happen)
     */
    public String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(body);
            return "sha256=" + toHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
