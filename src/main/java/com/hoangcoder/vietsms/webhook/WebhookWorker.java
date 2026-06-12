package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.common.VietsmsMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Gửi webhook từ outbox: poll PENDING đến hạn, POST có chữ ký HMAC, retry với backoff.
 *
 * <p>CỐ Ý không có {@code @Transactional} bao quanh vòng gửi — không bao giờ giữ
 * transaction DB trong lúc chờ HTTP (5s timeout × 50 row = treo pool). Mỗi delivery
 * được save riêng sau attempt; app chạy single-instance nên không cần row locking
 * (ghi chú trade-off trong docs/webhooks.md).
 *
 * <p>Backoff: 1m → 5m → 30m; sau {@value #MAX_ATTEMPTS} lần thất bại → DEAD (dead-letter,
 * xem qua GET /v1/webhooks/{id}/deliveries?status=DEAD).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookWorker {

    static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)};
    static final int MAX_ATTEMPTS = 4;

    @Value("${vietsms.webhooks.enabled:true}")
    private boolean enabled;

    @Value("${vietsms.webhooks.timeout-ms:5000}")
    private int timeoutMs;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final HmacSigner signer;
    private final VietsmsMetrics metrics;

    private RestClient restClient;

    @PostConstruct
    void initClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    @Scheduled(fixedDelayString = "${vietsms.webhooks.worker-interval-ms:1000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            deliverDue(Instant.now());
        } catch (Exception e) {
            log.error("WebhookWorker tick failed: {}", e.getMessage(), e);
        }
    }

    /** Tách riêng + nhận Instant để test điều khiển được thời gian (như DeliveryWorker). */
    public void deliverDue(Instant now) {
        List<WebhookDelivery> due = deliveryRepository
                .findTop50ByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                        WebhookDeliveryStatus.PENDING, now);
        for (WebhookDelivery d : due) {
            attempt(d, now);
            deliveryRepository.save(d);
        }
    }

    private void attempt(WebhookDelivery d, Instant now) {
        WebhookEndpoint ep = endpointRepository.findById(d.getEndpointId()).orElse(null);
        if (ep == null || !Boolean.TRUE.equals(ep.getEnabled())) {
            d.setStatus(WebhookDeliveryStatus.DEAD);
            d.setNextRetryAt(null);
            d.setLastError("endpoint missing or disabled");
            metrics.webhookDead();
            return;
        }
        byte[] body = d.getPayload().getBytes(StandardCharsets.UTF_8);
        long started = System.nanoTime();
        try {
            restClient.post()
                    .uri(ep.getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-VietSMS-Signature", signer.sign(body, ep.getSecret()))
                    .header("X-VietSMS-Event", d.getEventType())
                    .header("X-VietSMS-Delivery-Id", String.valueOf(d.getId()))
                    .body(d.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            onSuccess(d, now);
        } catch (RestClientResponseException e) {
            onFailure(d, now, "HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            onFailure(d, now, truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        } finally {
            metrics.webhookLatency(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private void onSuccess(WebhookDelivery d, Instant now) {
        d.setStatus(WebhookDeliveryStatus.DELIVERED);
        d.setDeliveredAt(now);
        d.setNextRetryAt(null);
        d.setLastError(null);
        metrics.webhookDelivered();
    }

    private void onFailure(WebhookDelivery d, Instant now, String error) {
        d.setAttempts(d.getAttempts() + 1);
        d.setLastError(truncate(error));
        if (d.getAttempts() >= MAX_ATTEMPTS) {
            d.setStatus(WebhookDeliveryStatus.DEAD);
            d.setNextRetryAt(null);
            metrics.webhookDead();
            log.warn("Webhook delivery {} dead-lettered after {} attempts: {}",
                    d.getId(), d.getAttempts(), error);
        } else {
            d.setNextRetryAt(now.plus(BACKOFF[d.getAttempts() - 1]));
            metrics.webhookFailedAttempt();
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 512 ? s.substring(0, 512) : s;
    }
}
