package com.hoangcoder.vietsms.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HmacSigner}.
 *
 * <p>Fixed test vector computed externally via Python:
 * <pre>
 *   python -c "import hmac,hashlib;print(hmac.new(b'test-secret',b'{\"a\":1}',hashlib.sha256).hexdigest())"
 *   => 179bf20a8b9040a32368814a68b0dc270823b5968498e0a73796c4202708ed8d
 * </pre>
 */
class HmacSignerTest {

    // HMAC-SHA256(key="test-secret", msg={"a":1})
    // Computed: python -c "import hmac,hashlib;print(hmac.new(b'test-secret',b'{\"a\":1}',hashlib.sha256).hexdigest())"
    private static final String EXPECTED_HEX =
            "179bf20a8b9040a32368814a68b0dc270823b5968498e0a73796c4202708ed8d";

    private final HmacSigner signer = new HmacSigner();

    @Test
    void sign_known_vector_matches_expected_sha256_prefix_and_hex() {
        byte[] body = "{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String result = signer.sign(body, "test-secret");

        assertThat(result).isEqualTo("sha256=" + EXPECTED_HEX);
    }

    @Test
    void sign_different_secret_produces_different_signature() {
        byte[] body = "{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String sig1 = signer.sign(body, "test-secret");
        String sig2 = signer.sign(body, "other-secret");

        assertThat(sig1).isNotEqualTo(sig2);
        assertThat(sig1).startsWith("sha256=");
        assertThat(sig2).startsWith("sha256=");
    }
}
