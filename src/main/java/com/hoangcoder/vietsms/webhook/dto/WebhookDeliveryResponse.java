package com.hoangcoder.vietsms.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hoangcoder.vietsms.webhook.WebhookDelivery;
import com.hoangcoder.vietsms.webhook.WebhookDeliveryStatus;

import java.time.Instant;

/**
 * Response item for GET /v1/webhooks/{id}/deliveries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookDeliveryResponse(
        Long id,
        String eventType,
        WebhookDeliveryStatus status,
        Integer attempts,
        String lastError,
        Instant createdAt,
        Instant deliveredAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(
                d.getId(),
                d.getEventType(),
                d.getStatus(),
                d.getAttempts(),
                d.getLastError(),
                d.getCreatedAt(),
                d.getDeliveredAt()
        );
    }
}
