package com.hoangcoder.vietsms.worker;

import com.hoangcoder.vietsms.common.VietsmsMetrics;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.webhook.WebhookEventType;
import com.hoangcoder.vietsms.webhook.WebhookOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

/**
 * Per-message state-machine transitions for SMS delivery.
 * Shared by the scheduled DeliveryWorker and any future consumers (e.g. Kafka).
 * Callers are responsible for persisting (saveAll) after calling these methods.
 */
@Component
@RequiredArgsConstructor
public class DeliveryProcessor {

    @Value("${vietsms.delivery.success-rate:0.95}")
    private double successRate;

    @Value("${vietsms.delivery.max-retries:3}")
    private int maxRetries;

    private final VietsmsMetrics metrics;
    private final WebhookOutbox webhookOutbox;

    /**
     * Transitions a QUEUED message to SENT.
     * Mutation only — caller must save.
     */
    public void markSent(SmsMessage m, Instant now) {
        m.setStatus(SmsStatus.SENT);
        m.setSentAt(now);
        m.setNextRetryAt(null);
        webhookOutbox.enqueueSmsEvent(m, WebhookEventType.SMS_SENT, now);
    }

    /**
     * Finalizes a SENT message: delivers or handles failure/retry.
     * Uses {@code rng} so callers can pass a deterministic source when needed.
     * Mutation only — caller must save.
     */
    public void finalizeOne(SmsMessage m, Instant now, Random rng) {
        if (rng.nextDouble() < successRate) {
            m.setStatus(SmsStatus.DELIVERED);
            m.setDeliveredAt(now);
            m.setErrorCode(null);
            metrics.smsDelivered();
            webhookOutbox.enqueueSmsEvent(m, WebhookEventType.SMS_DELIVERED, now);
        } else {
            handleFailure(m, now);
        }
    }

    private void handleFailure(SmsMessage m, Instant now) {
        m.setRetryCount(m.getRetryCount() + 1);
        m.setErrorCode("CARRIER_REJECTED");
        if (m.getRetryCount() >= maxRetries) {
            m.setStatus(SmsStatus.FAILED);
            m.setNextRetryAt(null);
            metrics.smsFailedTerminal();
            webhookOutbox.enqueueSmsEvent(m, WebhookEventType.SMS_FAILED, now);
            return;
        }
        long backoffSeconds = (long) Math.pow(2, m.getRetryCount() + 1);
        m.setStatus(SmsStatus.QUEUED);
        m.setNextRetryAt(now.plusSeconds(backoffSeconds));
        m.setSentAt(null);
        metrics.smsRetried();
    }
}
