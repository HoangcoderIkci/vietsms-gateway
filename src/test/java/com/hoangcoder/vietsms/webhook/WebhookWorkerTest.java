package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.security.ApiKeyService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WebhookWorkerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PAYLOAD = "{\"event\":\"sms.delivered\",\"data\":{\"id\":1}}";

    @Autowired ApiKeyService apiKeyService;
    @Autowired WebhookEndpointRepository endpointRepository;
    @Autowired WebhookDeliveryRepository deliveryRepository;
    @Autowired WebhookWorker worker;
    @Autowired HmacSigner signer;

    private MockWebServer server;
    private final List<Long> createdEndpoints = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void cleanup() throws Exception {
        server.shutdown();
        for (Long epId : createdEndpoints) {
            for (WebhookDeliveryStatus s : WebhookDeliveryStatus.values()) {
                deliveryRepository.deleteAll(
                        deliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(epId, s));
            }
            endpointRepository.deleteById(epId);
        }
        createdEndpoints.clear();
    }

    private WebhookEndpoint endpoint() {
        var key = apiKeyService.issue("wh-worker-" + System.nanoTime(), "t@example.com", 100).entity();
        WebhookEndpoint ep = endpointRepository.save(WebhookEndpoint.builder()
                .apiKeyId(key.getId())
                .url(server.url("/hook").toString())
                .secret(SECRET)
                .events("sms.delivered")
                .enabled(true)
                .createdAt(Instant.now())
                .build());
        createdEndpoints.add(ep.getId());
        return ep;
    }

    private WebhookDelivery pendingDelivery(Long endpointId, Instant due) {
        return deliveryRepository.save(WebhookDelivery.builder()
                .endpointId(endpointId)
                .eventType(WebhookEventType.SMS_DELIVERED.getWire())
                .payload(PAYLOAD)
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextRetryAt(due)
                .createdAt(due)
                .build());
    }

    private WebhookDelivery fresh(Long id) {
        return deliveryRepository.findById(id).orElseThrow();
    }

    @Test
    void happy_path_delivers_with_signed_headers() throws Exception {
        WebhookEndpoint ep = endpoint();
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS); // H2 lưu micros — millis để roundtrip exact
        WebhookDelivery d = pendingDelivery(ep.getId(), t0.minusSeconds(1));
        server.enqueue(new MockResponse().setResponseCode(200));

        worker.deliverDue(t0);

        WebhookDelivery after = fresh(d.getId());
        assertThat(after.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(after.getDeliveredAt()).isNotNull();
        assertThat(after.getLastError()).isNull();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("X-VietSMS-Event")).isEqualTo("sms.delivered");
        assertThat(req.getHeader("X-VietSMS-Delivery-Id")).isEqualTo(String.valueOf(d.getId()));
        assertThat(req.getHeader("X-VietSMS-Signature"))
                .isEqualTo(signer.sign(PAYLOAD.getBytes(StandardCharsets.UTF_8), SECRET));
        assertThat(req.getBody().readUtf8()).isEqualTo(PAYLOAD);
    }

    @Test
    void failure_schedules_backoff_1m_5m_30m_then_succeeds() throws Exception {
        WebhookEndpoint ep = endpoint();
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS); // H2 lưu micros — millis để roundtrip exact
        WebhookDelivery d = pendingDelivery(ep.getId(), t0.minusSeconds(1));

        // Attempt 1: 500 -> retry sau 1 phút
        server.enqueue(new MockResponse().setResponseCode(500));
        worker.deliverDue(t0);
        WebhookDelivery a1 = fresh(d.getId());
        assertThat(a1.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(a1.getAttempts()).isEqualTo(1);
        assertThat(a1.getLastError()).contains("500");
        assertThat(a1.getNextRetryAt()).isEqualTo(t0.plus(Duration.ofMinutes(1)));

        // Chưa tới hạn -> không gửi
        worker.deliverDue(t0.plusSeconds(30));
        assertThat(server.getRequestCount()).isEqualTo(1);

        // Attempt 2 (đến hạn): 500 -> retry sau 5 phút
        server.enqueue(new MockResponse().setResponseCode(500));
        Instant t1 = t0.plus(Duration.ofMinutes(1)).plusSeconds(1);
        worker.deliverDue(t1);
        WebhookDelivery a2 = fresh(d.getId());
        assertThat(a2.getAttempts()).isEqualTo(2);
        assertThat(a2.getNextRetryAt()).isEqualTo(t1.plus(Duration.ofMinutes(5)));

        // Attempt 3 (đến hạn): 200 -> DELIVERED
        server.enqueue(new MockResponse().setResponseCode(200));
        worker.deliverDue(t1.plus(Duration.ofMinutes(5)).plusSeconds(1));
        assertThat(fresh(d.getId()).getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
    }

    @Test
    void dead_letter_after_four_failures() {
        WebhookEndpoint ep = endpoint();
        Instant t = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        WebhookDelivery d = pendingDelivery(ep.getId(), t.minusSeconds(1));

        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
            worker.deliverDue(t);
            t = fresh(d.getId()).getNextRetryAt() != null
                    ? fresh(d.getId()).getNextRetryAt().plusSeconds(1) : t;
        }

        WebhookDelivery after = fresh(d.getId());
        assertThat(after.getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD);
        assertThat(after.getAttempts()).isEqualTo(4);
        assertThat(after.getNextRetryAt()).isNull();
        assertThat(after.getLastError()).contains("500");
    }

    @Test
    void missing_or_disabled_endpoint_dead_letters_immediately() {
        WebhookEndpoint ep = endpoint();
        ep.setEnabled(false);
        endpointRepository.save(ep);
        WebhookDelivery d = pendingDelivery(ep.getId(), Instant.now().minusSeconds(1));

        worker.deliverDue(Instant.now());

        WebhookDelivery after = fresh(d.getId());
        assertThat(after.getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD);
        assertThat(after.getLastError()).contains("disabled");
        assertThat(server.getRequestCount()).isZero();
    }
}
