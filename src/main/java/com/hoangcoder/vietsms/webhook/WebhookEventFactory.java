package com.hoangcoder.vietsms.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangcoder.vietsms.common.PhoneNormalizer;
import com.hoangcoder.vietsms.sms.SmsMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds JSON event payloads for webhook deliveries.
 *
 * <p>Phone numbers in the payload are always masked via {@link PhoneNormalizer#mask(String)}
 * so that the full subscriber number is never included in outgoing webhook bodies.
 *
 * <p>Timestamp is passed as a parameter so callers (and tests) control the exact instant,
 * avoiding the need for a Clock bean that would conflict with the existing Spring context.
 */
@Component
@RequiredArgsConstructor
public class WebhookEventFactory {

    private final ObjectMapper objectMapper;

    /**
     * Builds a JSON string for an SMS-related webhook event.
     *
     * <p>Shape:
     * <pre>{@code
     * {
     *   "event": "sms.delivered",
     *   "timestamp": "2024-01-01T00:00:00Z",
     *   "data": {
     *     "id": 123,
     *     "to": "+84****321",
     *     "status": "DELIVERED",
     *     "clientMessageId": "..."
     *   }
     * }
     * }</pre>
     *
     * @param msg       the SMS message
     * @param type      the webhook event type (determines the "event" wire name)
     * @param timestamp the timestamp to embed in the payload (pass {@code Instant.now()} in production)
     * @return serialized JSON string
     * @throws IllegalStateException if Jackson serialization fails
     */
    public String smsPayload(SmsMessage msg, WebhookEventType type, Instant timestamp) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", msg.getId());
            data.put("to", PhoneNormalizer.mask(msg.getToPhone()));
            data.put("status", msg.getStatus() != null ? msg.getStatus().name() : null);
            data.put("clientMessageId", msg.getClientMessageId());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", type.getWire());
            payload.put("timestamp", timestamp.toString());
            payload.put("data", data);

            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }
}
