package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.common.NotFoundException;
import com.hoangcoder.vietsms.webhook.dto.RegisterWebhookRequest;
import com.hoangcoder.vietsms.webhook.dto.RegisterWebhookResponse;
import com.hoangcoder.vietsms.webhook.dto.WebhookDeliveryResponse;
import com.hoangcoder.vietsms.webhook.dto.WebhookEndpointResponse;
import com.hoangcoder.vietsms.webhook.exceptions.WebhookException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final int MAX_ENDPOINTS_PER_KEY = 5;
    private static final int SECRET_BYTES = 16; // 16 bytes = 32 hex chars

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final UrlValidator urlValidator;
    private final WebhookEventFactory eventFactory;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public RegisterWebhookResponse register(Long apiKeyId, RegisterWebhookRequest request) {
        // Validate URL (SSRF-safe)
        urlValidator.validate(request.url());

        // Validate event wire names — unknown name → 400
        List<String> validatedEvents = request.events().stream()
                .map(wire -> {
                    try {
                        return WebhookEventType.fromWire(wire).getWire();
                    } catch (IllegalArgumentException e) {
                        throw new WebhookException(
                                "VALIDATION_ERROR",
                                "Unknown webhook event type: " + wire,
                                HttpStatus.BAD_REQUEST
                        );
                    }
                })
                .distinct()
                .collect(Collectors.toList());

        // Enforce per-key limit
        long existing = endpointRepository.countByApiKeyId(apiKeyId);
        if (existing >= MAX_ENDPOINTS_PER_KEY) {
            throw new WebhookException(
                    "WEBHOOK_LIMIT_REACHED",
                    "Maximum of " + MAX_ENDPOINTS_PER_KEY + " webhook endpoints per API key",
                    HttpStatus.CONFLICT
            );
        }

        // Generate 32-char lowercase hex secret
        byte[] buf = new byte[SECRET_BYTES];
        secureRandom.nextBytes(buf);
        String secret = HexFormat.of().formatHex(buf);

        String eventsCsv = String.join(",", validatedEvents);

        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .apiKeyId(apiKeyId)
                .url(request.url())
                .secret(secret)
                .events(eventsCsv)
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        endpoint = endpointRepository.save(endpoint);
        return RegisterWebhookResponse.from(endpoint, secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> list(Long apiKeyId) {
        return endpointRepository.findByApiKeyIdAndEnabledTrue(apiKeyId).stream()
                .map(WebhookEndpointResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long apiKeyId, Long endpointId) {
        WebhookEndpoint endpoint = endpointRepository.findById(endpointId)
                .filter(e -> e.getApiKeyId().equals(apiKeyId))
                .orElseThrow(() -> new NotFoundException("Webhook endpoint " + endpointId + " not found"));

        endpointRepository.delete(endpoint);
    }

    /**
     * Enqueues a {@code webhook.test} delivery for the given endpoint.
     * Ownership check mirrors DELETE — returns 404 if not owned by caller.
     *
     * @return the ID of the created WebhookDelivery row
     */
    @Transactional
    public long fireTest(Long apiKeyId, Long endpointId) {
        WebhookEndpoint endpoint = endpointRepository.findById(endpointId)
                .filter(e -> e.getApiKeyId().equals(apiKeyId))
                .orElseThrow(() -> new NotFoundException("Webhook endpoint " + endpointId + " not found"));

        Instant now = Instant.now();
        String payload = eventFactory.testPayload(now);

        WebhookDelivery delivery = deliveryRepository.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.WEBHOOK_TEST.getWire())
                .payload(payload)
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextRetryAt(now)
                .createdAt(now)
                .build());

        return delivery.getId();
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> listDeliveries(Long apiKeyId, Long endpointId, WebhookDeliveryStatus status) {
        // Ownership check — same 404 behavior as delete (do not leak existence)
        endpointRepository.findById(endpointId)
                .filter(e -> e.getApiKeyId().equals(apiKeyId))
                .orElseThrow(() -> new NotFoundException("Webhook endpoint " + endpointId + " not found"));

        return deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(endpointId, status).stream()
                .map(WebhookDeliveryResponse::from)
                .collect(Collectors.toList());
    }
}
