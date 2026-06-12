package com.hoangcoder.vietsms;

import com.hoangcoder.vietsms.security.ApiKeyService;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsRepository;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import com.hoangcoder.vietsms.sms.SmsService;
import com.hoangcoder.vietsms.webhook.WebhookDeliveryRepository;
import com.hoangcoder.vietsms.webhook.WebhookDeliveryStatus;
import com.hoangcoder.vietsms.webhook.WebhookEndpoint;
import com.hoangcoder.vietsms.webhook.WebhookEndpointRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PostgresEndToEndIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ApiKeyService apiKeyService;
    @Autowired SmsService smsService;
    @Autowired SmsRepository smsRepository;
    @Autowired WebhookEndpointRepository endpointRepository;
    @Autowired WebhookDeliveryRepository deliveryRepository;

    @Test
    void migrations_apply_cleanly_on_postgres() {
        // Context loaded + repositories usable => V1-V6 ran without error on PG.
        var key = apiKeyService.issue("pg-smoke", "smoke@example.com", 10).entity();
        assertThat(key.getId()).isNotNull();

        var found = apiKeyService.authenticate(
                apiKeyService.issue("pg-smoke2", "smoke2@example.com", 10).rawKey());
        assertThat(found).isPresent();
    }

    @Test
    void full_sms_lifecycle_with_webhook_outbox_on_postgres() {
        var issuedKey = apiKeyService.issue("pg-e2e", "e2e@example.com", 100);
        Long apiKeyId = issuedKey.entity().getId();

        // Register a webhook endpoint for sms.delivered (unreachable URL is intentional)
        WebhookEndpoint ep = endpointRepository.save(WebhookEndpoint.builder()
                .apiKeyId(apiKeyId)
                .url("https://receiver.invalid/hook")
                .secret("s".repeat(32))
                .events("sms.delivered")
                .enabled(true)
                .createdAt(Instant.now())
                .build());

        // Send SMS
        SmsMessage msg = smsService.send(apiKeyId, new SendSmsRequest("0987654321", "hello pg", null));
        assertThat(msg.getStatus()).isEqualTo(SmsStatus.QUEUED);

        // Await terminal status on real Postgres
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    SmsMessage fresh = smsRepository.findById(msg.getId()).orElseThrow();
                    assertThat(fresh.getStatus()).isIn(SmsStatus.DELIVERED, SmsStatus.FAILED);
                });

        SmsMessage finalMsg = smsRepository.findById(msg.getId()).orElseThrow();

        if (finalMsg.getStatus() == SmsStatus.DELIVERED) {
            // Assert a webhook_delivery row exists for our endpoint in any status
            // (worker attempted delivery to receiver.invalid — may be PENDING, FAILED, or DEAD)
            Awaitility.await()
                    .atMost(15, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofMillis(300))
                    .untilAsserted(() -> {
                        List<com.hoangcoder.vietsms.webhook.WebhookDelivery> rows =
                                Arrays.stream(WebhookDeliveryStatus.values())
                                        .flatMap(s -> deliveryRepository
                                                .findByEndpointIdAndStatusOrderByCreatedAtDesc(ep.getId(), s)
                                                .stream())
                                        .toList();
                        assertThat(rows).isNotEmpty();
                    });
        }
        // If FAILED: no delivery row expected (outbox only fires sms.delivered events)
        // Either way the test passed if terminal state was reached on real PG.
    }
}
