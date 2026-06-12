package com.hoangcoder.vietsms.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hoangcoder.vietsms.webhook.WebhookEndpoint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Response for GET /v1/webhooks — does NOT include secret.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookEndpointResponse(
        Long id,
        String url,
        List<String> events,
        Boolean enabled,
        Instant createdAt
) {
    public static WebhookEndpointResponse from(WebhookEndpoint e) {
        List<String> eventList = Arrays.stream(e.getEvents().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new WebhookEndpointResponse(
                e.getId(),
                e.getUrl(),
                eventList,
                e.getEnabled(),
                e.getCreatedAt()
        );
    }
}
