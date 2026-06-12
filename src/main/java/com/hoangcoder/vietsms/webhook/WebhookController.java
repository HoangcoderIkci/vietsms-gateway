package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.webhook.dto.RegisterWebhookRequest;
import com.hoangcoder.vietsms.webhook.dto.RegisterWebhookResponse;
import com.hoangcoder.vietsms.webhook.dto.WebhookDeliveryResponse;
import com.hoangcoder.vietsms.webhook.dto.WebhookEndpointResponse;
import com.hoangcoder.vietsms.webhook.exceptions.WebhookException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Manage webhook endpoint registrations and inspect delivery history")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    @Operation(summary = "Register a new webhook endpoint",
            description = "Creates a new endpoint subscribed to the given events. " +
                    "Returns 201 with the one-time secret — it is never returned again.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Webhook endpoint registered"),
            @ApiResponse(responseCode = "400", description = "Validation error or unknown event name"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header"),
            @ApiResponse(responseCode = "409", description = "Maximum of 5 endpoints per API key reached"),
            @ApiResponse(responseCode = "422", description = "URL is invalid or resolves to a private address")
    })
    public ResponseEntity<RegisterWebhookResponse> register(
            @AuthenticationPrincipal ApiKey key,
            @Valid @RequestBody RegisterWebhookRequest request) {
        RegisterWebhookResponse response = webhookService.register(key.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List webhook endpoints for the caller's API key",
            description = "Returns all enabled endpoints. Secret is never included in list responses.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of endpoints (secret omitted)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header")
    })
    public List<WebhookEndpointResponse> list(@AuthenticationPrincipal ApiKey key) {
        return webhookService.list(key.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook endpoint",
            description = "Permanently deletes the endpoint. Returns 404 if not found or not owned by the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Endpoint deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found or not owned by caller")
    })
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal ApiKey key,
            @PathVariable Long id) {
        webhookService.delete(key.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Fire a test delivery to a webhook endpoint",
            description = "Enqueues a webhook.test delivery (PENDING) for the given endpoint. " +
                    "The WebhookWorker will pick it up on its next tick and POST to the registered URL. " +
                    "Returns 404 if the endpoint is not found or not owned by the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Test delivery enqueued — deliveryId returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found or not owned by caller")
    })
    public ResponseEntity<Map<String, Long>> test(
            @AuthenticationPrincipal ApiKey key,
            @PathVariable Long id) {
        long deliveryId = webhookService.fireTest(key.getId(), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("deliveryId", deliveryId));
    }

    @GetMapping("/{id}/deliveries")
    @Operation(summary = "List delivery attempts for a webhook endpoint",
            description = "Requires `status` query param (PENDING, DELIVERED, FAILED, or DEAD). " +
                    "Returns 400 if status is missing. Returns 404 if endpoint not found or not owned by caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of delivery attempts"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid status param"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found or not owned by caller")
    })
    public List<WebhookDeliveryResponse> deliveries(
            @AuthenticationPrincipal ApiKey key,
            @PathVariable Long id,
            @RequestParam(required = true) WebhookDeliveryStatus status) {
        return webhookService.listDeliveries(key.getId(), id, status);
    }
}
