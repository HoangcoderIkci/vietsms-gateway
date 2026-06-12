package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.security.ApiKeyService;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import com.hoangcoder.vietsms.sms.SmsService;
import com.hoangcoder.vietsms.worker.DeliveryWorker;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WebhookOutboxTest {

    @Autowired ApiKeyService apiKeyService;
    @Autowired SmsService smsService;
    @Autowired WebhookEndpointRepository endpointRepository;
    @Autowired WebhookDeliveryRepository deliveryRepository;
    @Autowired WebhookOutbox outbox;
    @Autowired DeliveryWorker worker;

    private WebhookEndpoint endpoint(Long apiKeyId, String events, boolean enabled) {
        return endpointRepository.save(WebhookEndpoint.builder()
                .apiKeyId(apiKeyId)
                .url("https://receiver.example.com/hook")
                .secret("a".repeat(32))
                .events(events)
                .enabled(enabled)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void enqueue_creates_pending_rows_only_for_subscribed_endpoints() {
        var key = apiKeyService.issue("outbox-1", "t@example.com", 100).entity();
        WebhookEndpoint subscribed = endpoint(key.getId(), "sms.delivered,sms.failed", true);
        WebhookEndpoint notSubscribed = endpoint(key.getId(), "sms.sent", true);
        SmsMessage msg = smsService.send(key.getId(), new SendSmsRequest("0987654321", "hi", null));

        Instant now = Instant.parse("2026-06-12T10:00:00Z");
        outbox.enqueueSmsEvent(msg, WebhookEventType.SMS_DELIVERED, now);

        var rows = deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(
                subscribed.getId(), WebhookDeliveryStatus.PENDING);
        assertThat(rows).hasSize(1);
        WebhookDelivery row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo("sms.delivered");
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getNextRetryAt()).isEqualTo(now);
        assertThat(row.getPayload()).contains("sms.delivered").doesNotContain("0987654321");

        assertThat(deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(
                notSubscribed.getId(), WebhookDeliveryStatus.PENDING)).isEmpty();
    }

    @Test
    void enqueue_skips_disabled_endpoint_and_global_flag_off() {
        var key = apiKeyService.issue("outbox-2", "t@example.com", 100).entity();
        WebhookEndpoint disabledEp = endpoint(key.getId(), "sms.delivered", false);
        SmsMessage msg = smsService.send(key.getId(), new SendSmsRequest("0987654321", "hi", null));

        outbox.enqueueSmsEvent(msg, WebhookEventType.SMS_DELIVERED, Instant.now());
        assertThat(deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(
                disabledEp.getId(), WebhookDeliveryStatus.PENDING)).isEmpty();

        WebhookEndpoint activeEp = endpoint(key.getId(), "sms.delivered", true);
        ReflectionTestUtils.setField(outbox, "enabled", false);
        try {
            outbox.enqueueSmsEvent(msg, WebhookEventType.SMS_DELIVERED, Instant.now());
            assertThat(deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(
                    activeEp.getId(), WebhookDeliveryStatus.PENDING)).isEmpty();
        } finally {
            ReflectionTestUtils.setField(outbox, "enabled", true);
        }
    }

    @Test
    void worker_sent_transition_enqueues_sms_sent_event() {
        var key = apiKeyService.issue("outbox-3", "t@example.com", 100).entity();
        WebhookEndpoint ep = endpoint(key.getId(), "sms.sent", true);
        smsService.send(key.getId(), new SendSmsRequest("0987654321", "hello", null));

        worker.tick();

        // Nền @Scheduled cũng có thể đã pick — dù đường nào, đúng 1 event sms.sent phải xuất hiện
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(
                        deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(
                                ep.getId(), WebhookDeliveryStatus.PENDING))
                        .anySatisfy(d -> assertThat(d.getEventType()).isEqualTo("sms.sent")));
    }
}
