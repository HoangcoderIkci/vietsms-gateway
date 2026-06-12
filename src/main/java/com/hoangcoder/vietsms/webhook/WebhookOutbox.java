package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.sms.SmsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox cho webhook: ghi {@link WebhookDelivery} row PENDING
 * trong CÙNG transaction với thay đổi trạng thái nghiệp vụ (SMS/OTP).
 *
 * <p>Tuyệt đối KHÔNG gọi HTTP ở đây — việc gửi là của WebhookWorker (poll outbox).
 * Nhờ đó: trạng thái SMS và sự kiện webhook hoặc cùng commit, hoặc cùng rollback,
 * không bao giờ lệch nhau, và transaction không bị treo theo network I/O.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookOutbox {

    @Value("${vietsms.webhooks.enabled:true}")
    private boolean enabled;

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEventFactory eventFactory;

    /**
     * Gọi từ bên trong transaction đang chuyển trạng thái SMS.
     * Tạo 1 delivery PENDING cho mỗi endpoint enabled có đăng ký event này.
     */
    public void enqueueSmsEvent(SmsMessage msg, WebhookEventType type, Instant now) {
        if (!enabled) {
            return;
        }
        List<WebhookEndpoint> endpoints =
                endpointRepository.findByApiKeyIdAndEnabledTrue(msg.getApiKeyId());
        if (endpoints.isEmpty()) {
            return;
        }
        String payload = null; // lazy: chỉ serialize khi có endpoint đăng ký
        for (WebhookEndpoint ep : endpoints) {
            if (!ep.getEventSet().contains(type)) {
                continue;
            }
            if (payload == null) {
                payload = eventFactory.smsPayload(msg, type, now);
            }
            deliveryRepository.save(WebhookDelivery.builder()
                    .endpointId(ep.getId())
                    .eventType(type.getWire())
                    .payload(payload)
                    .status(WebhookDeliveryStatus.PENDING)
                    .attempts(0)
                    .nextRetryAt(now)
                    .createdAt(now)
                    .build());
        }
    }
}
