package com.hoangcoder.vietsms.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebhookEventFactory}.
 *
 * <p>Uses a standalone ObjectMapper (no Spring context needed) — same mapper
 * Spring Boot auto-configures, so serialization behavior is identical.
 */
class WebhookEventFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WebhookEventFactory factory = new WebhookEventFactory(MAPPER);

    @Test
    void smsPayload_contains_correct_event_wire_name() throws Exception {
        SmsMessage msg = buildMessage("+84987654321");

        String json = factory.smsPayload(msg, WebhookEventType.SMS_DELIVERED, Instant.now());

        JsonNode root = MAPPER.readTree(json);
        assertThat(root.get("event").asText()).isEqualTo("sms.delivered");
    }

    @Test
    void smsPayload_phone_is_masked_and_not_full_number() throws Exception {
        String fullPhone = "+84987654321";
        SmsMessage msg = buildMessage(fullPhone);

        String json = factory.smsPayload(msg, WebhookEventType.SMS_DELIVERED, Instant.now());

        JsonNode root = MAPPER.readTree(json);
        String maskedTo = root.get("data").get("to").asText();

        // Must NOT contain the full original phone number
        assertThat(json).doesNotContain(fullPhone);

        // Masking pattern: first 3 chars + "****" + last 3 chars  => "+84****321"
        assertThat(maskedTo).isEqualTo("+84****321");
        assertThat(maskedTo).contains("****");
    }

    @Test
    void smsPayload_data_id_and_status_are_correct() throws Exception {
        SmsMessage msg = buildMessage("+84987654321");

        String json = factory.smsPayload(msg, WebhookEventType.SMS_SENT, Instant.now());

        JsonNode data = MAPPER.readTree(json).get("data");
        assertThat(data.get("id").asLong()).isEqualTo(42L);
        assertThat(data.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(data.get("clientMessageId").asText()).isEqualTo("client-ref-001");
    }

    @Test
    void smsPayload_timestamp_is_iso8601_and_matches_provided_instant() throws Exception {
        Instant fixedTime = Instant.parse("2024-06-01T12:00:00Z");
        SmsMessage msg = buildMessage("+84987654321");

        String json = factory.smsPayload(msg, WebhookEventType.SMS_SENT, fixedTime);

        JsonNode root = MAPPER.readTree(json);
        assertThat(root.get("timestamp").asText()).isEqualTo("2024-06-01T12:00:00Z");
    }

    @Test
    void smsPayload_is_valid_json() throws Exception {
        SmsMessage msg = buildMessage("+84987654321");

        String json = factory.smsPayload(msg, WebhookEventType.SMS_FAILED, Instant.now());

        // Should not throw
        JsonNode root = MAPPER.readTree(json);
        assertThat(root.isObject()).isTrue();
        assertThat(root.has("event")).isTrue();
        assertThat(root.has("timestamp")).isTrue();
        assertThat(root.has("data")).isTrue();
    }

    // ---------- helpers ----------

    private SmsMessage buildMessage(String phone) {
        return SmsMessage.builder()
                .id(42L)
                .apiKeyId(1L)
                .toPhone(phone)
                .content("Test content")
                .status(SmsStatus.DELIVERED)
                .clientMessageId("client-ref-001")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }
}
