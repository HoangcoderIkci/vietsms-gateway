package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WebhookRepositoryTest {

    @Autowired
    WebhookEndpointRepository endpointRepo;

    @Autowired
    WebhookDeliveryRepository deliveryRepo;

    @Autowired
    ApiKeyService apiKeyService;

    ApiKey apiKey;
    WebhookEndpoint endpoint;

    @BeforeEach
    void setUp() {
        apiKey = apiKeyService.issue(
                "webhook-test-" + System.nanoTime(), "t@example.com", 100).entity();

        endpoint = endpointRepo.save(WebhookEndpoint.builder()
                .apiKeyId(apiKey.getId())
                .url("https://example.com/hook")
                .secret("supersecret")
                .events("sms.sent,sms.failed")
                .enabled(true)
                .createdAt(Instant.now())
                .build());
    }

    // --- WebhookEndpoint ---

    @Test
    void endpoint_roundtrip_and_event_set_parsing() {
        WebhookEndpoint loaded = endpointRepo.findById(endpoint.getId()).orElseThrow();

        assertThat(loaded.getUrl()).isEqualTo("https://example.com/hook");
        assertThat(loaded.getEnabled()).isTrue();

        Set<WebhookEventType> events = loaded.getEventSet();
        assertThat(events).containsExactlyInAnyOrder(
                WebhookEventType.SMS_SENT, WebhookEventType.SMS_FAILED);
    }

    @Test
    void findByApiKeyIdAndEnabledTrue_returns_only_enabled() {
        // Save a disabled endpoint for the same key
        endpointRepo.save(WebhookEndpoint.builder()
                .apiKeyId(apiKey.getId())
                .url("https://example.com/disabled")
                .secret("secret2")
                .events("sms.sent")
                .enabled(false)
                .createdAt(Instant.now())
                .build());

        List<WebhookEndpoint> active = endpointRepo.findByApiKeyIdAndEnabledTrue(apiKey.getId());
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getUrl()).isEqualTo("https://example.com/hook");
    }

    @Test
    void countByApiKeyId_counts_all_regardless_of_enabled() {
        endpointRepo.save(WebhookEndpoint.builder()
                .apiKeyId(apiKey.getId())
                .url("https://example.com/another")
                .secret("secret3")
                .events("sms.sent")
                .enabled(false)
                .createdAt(Instant.now())
                .build());

        assertThat(endpointRepo.countByApiKeyId(apiKey.getId())).isEqualTo(2);
    }

    // --- WebhookDelivery ---

    @Test
    void delivery_roundtrip() {
        WebhookDelivery saved = deliveryRepo.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.SMS_SENT.getWire())
                .payload("{\"id\":1}")
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextRetryAt(Instant.now().minusSeconds(5))
                .createdAt(Instant.now())
                .build());

        WebhookDelivery loaded = deliveryRepo.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(loaded.getEventType()).isEqualTo("sms.sent");
    }

    @Test
    void findTop50_only_returns_due_pending_rows() {
        Instant now = Instant.now();

        // Due PENDING — should be returned
        WebhookDelivery due = deliveryRepo.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.SMS_SENT.getWire())
                .payload("{\"case\":\"due\"}")
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextRetryAt(now.minusSeconds(10))
                .createdAt(now)
                .build());

        // Future PENDING — must NOT be returned
        deliveryRepo.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.SMS_SENT.getWire())
                .payload("{\"case\":\"future\"}")
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextRetryAt(now.plusSeconds(3600))
                .createdAt(now)
                .build());

        // DELIVERED — must NOT be returned
        deliveryRepo.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.SMS_DELIVERED.getWire())
                .payload("{\"case\":\"delivered\"}")
                .status(WebhookDeliveryStatus.DELIVERED)
                .attempts(1)
                .nextRetryAt(now.minusSeconds(5))
                .createdAt(now)
                .deliveredAt(now)
                .build());

        List<WebhookDelivery> due50 = deliveryRepo
                .findTop50ByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                        WebhookDeliveryStatus.PENDING, now);

        // Assert theo id (query là toàn cục — test khác có thể đã commit rows riêng của chúng)
        List<Long> ids = due50.stream().map(WebhookDelivery::getId).toList();
        assertThat(ids).contains(due.getId());
        assertThat(due50).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(d.getNextRetryAt()).isBefore(now);
        });
    }

    @Test
    void findByEndpointIdAndStatus_returns_correct_subset() {
        Instant now = Instant.now();

        deliveryRepo.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.SMS_SENT.getWire())
                .payload("{\"a\":1}")
                .status(WebhookDeliveryStatus.FAILED)
                .attempts(3)
                .createdAt(now.minusSeconds(20))
                .build());

        deliveryRepo.save(WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(WebhookEventType.SMS_SENT.getWire())
                .payload("{\"b\":2}")
                .status(WebhookDeliveryStatus.DELIVERED)
                .attempts(1)
                .createdAt(now)
                .deliveredAt(now)
                .build());

        List<WebhookDelivery> failed = deliveryRepo
                .findByEndpointIdAndStatusOrderByCreatedAtDesc(
                        endpoint.getId(), WebhookDeliveryStatus.FAILED);

        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
    }
}
