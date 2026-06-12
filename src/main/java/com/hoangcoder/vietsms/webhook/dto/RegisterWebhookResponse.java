package com.hoangcoder.vietsms.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hoangcoder.vietsms.webhook.WebhookEndpoint;

import java.time.Instant;
import java.util.List;

/**
 * Response for POST /v1/webhooks — includes secret (one-time reveal).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterWebhookResponse(
        Long id,
        String url,
        List<String> events,
        String secret
) {
    public static RegisterWebhookResponse from(WebhookEndpoint e, String secret) {
        return new RegisterWebhookResponse(
                e.getId(),
                e.getUrl(),
                List.of(e.getEvents().split(",")),
                secret
        );
    }
}
