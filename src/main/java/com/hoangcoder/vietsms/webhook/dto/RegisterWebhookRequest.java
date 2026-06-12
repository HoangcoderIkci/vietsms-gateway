package com.hoangcoder.vietsms.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RegisterWebhookRequest(
        @Schema(description = "HTTPS or HTTP URL to call on events", example = "https://my-app.example.com/hooks/vietsms")
        @NotBlank
        String url,

        @Schema(description = "List of event wire names to subscribe to", example = "[\"sms.delivered\", \"sms.failed\"]")
        @NotEmpty
        List<String> events
) {}
