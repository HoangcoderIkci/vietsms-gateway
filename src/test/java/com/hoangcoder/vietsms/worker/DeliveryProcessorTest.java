package com.hoangcoder.vietsms.worker;

import com.hoangcoder.vietsms.common.VietsmsMetrics;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.webhook.WebhookEventType;
import com.hoangcoder.vietsms.webhook.WebhookOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pure unit tests for DeliveryProcessor state-machine logic.
 *
 * <p>Determinism strategy: instead of relying on probabilistic success-rate=1.0/0.0
 * through property injection (which would require a Spring context), we pass a
 * deterministic {@link Random} stub whose {@code nextDouble()} returns a fixed value:
 * <ul>
 *   <li>0.0 → always less than any positive successRate → always succeeds (DELIVERED)</li>
 *   <li>1.0 → always >= successRate → always fails (retry/FAILED)</li>
 * </ul>
 * No @SpringBootTest needed — all collaborators are Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryProcessorTest {

    @Mock VietsmsMetrics metrics;
    @Mock WebhookOutbox webhookOutbox;

    DeliveryProcessor processor;

    /** successRate=0.95, maxRetries=3 (matches production defaults). */
    @BeforeEach
    void setup() {
        processor = new DeliveryProcessor(metrics, webhookOutbox);
        ReflectionTestUtils.setField(processor, "successRate", 0.95);
        ReflectionTestUtils.setField(processor, "maxRetries", 3);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private SmsMessage queuedSms() {
        return SmsMessage.builder()
                .id(1L)
                .apiKeyId(1L)
                .toPhone("0987654321")
                .content("hello")
                .status(SmsStatus.QUEUED)
                .retryCount(0)
                .nextRetryAt(null)
                .createdAt(Instant.now())
                .build();
    }

    /** Random that always returns the given fixed value from nextDouble(). */
    private static Random fixedRng(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    // -----------------------------------------------------------------------
    // markSent
    // -----------------------------------------------------------------------

    @Test
    void markSent_transitions_to_SENT_and_enqueues_SMS_SENT_webhook() {
        SmsMessage m = queuedSms();
        Instant now = Instant.parse("2026-06-20T10:00:00Z");

        processor.markSent(m, now);

        assertThat(m.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(m.getSentAt()).isEqualTo(now);
        assertThat(m.getNextRetryAt()).isNull();

        verify(webhookOutbox).enqueueSmsEvent(eq(m), eq(WebhookEventType.SMS_SENT), eq(now));
    }

    // -----------------------------------------------------------------------
    // finalizeOne — success path
    // -----------------------------------------------------------------------

    @Test
    void finalizeOne_success_sets_DELIVERED_and_fires_metrics_and_webhook() {
        SmsMessage m = queuedSms();
        m.setStatus(SmsStatus.SENT);
        Instant now = Instant.parse("2026-06-20T10:01:00Z");

        // nextDouble() = 0.0 < successRate(0.95) → success branch
        processor.finalizeOne(m, now, fixedRng(0.0));

        assertThat(m.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(m.getDeliveredAt()).isEqualTo(now);
        assertThat(m.getErrorCode()).isNull();

        verify(metrics).smsDelivered();
        verify(webhookOutbox).enqueueSmsEvent(eq(m), eq(WebhookEventType.SMS_DELIVERED), eq(now));

        // No failure-related calls
        verify(metrics, never()).smsRetried();
        verify(metrics, never()).smsFailedTerminal();
        verify(webhookOutbox, never()).enqueueSmsEvent(any(), eq(WebhookEventType.SMS_FAILED), any());
    }

    // -----------------------------------------------------------------------
    // finalizeOne — failure with retry budget remaining
    // -----------------------------------------------------------------------

    @Test
    void finalizeOne_failure_with_budget_sets_QUEUED_with_exponential_backoff() {
        SmsMessage m = queuedSms();
        m.setStatus(SmsStatus.SENT);
        m.setRetryCount(0);        // first attempt
        m.setSentAt(Instant.now());
        Instant now = Instant.parse("2026-06-20T10:02:00Z");

        // nextDouble() = 1.0 >= successRate(0.95) → failure branch
        processor.finalizeOne(m, now, fixedRng(1.0));

        // retryCount incremented before check: 0 → 1, still < maxRetries(3)
        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(m.getErrorCode()).isEqualTo("CARRIER_REJECTED");
        assertThat(m.getSentAt()).isNull();

        // backoff = 2^(retryCount+1) = 2^(1+1) = 4 seconds
        // Note: retryCount is already incremented when backoff is computed:
        // backoffSeconds = 2^(m.getRetryCount() + 1) where retryCount=1 → 2^2 = 4
        long expectedBackoff = (long) Math.pow(2, m.getRetryCount() + 1); // 2^2 = 4
        assertThat(m.getNextRetryAt()).isEqualTo(now.plusSeconds(expectedBackoff));

        verify(metrics).smsRetried();
        verify(metrics, never()).smsFailedTerminal();
        verify(webhookOutbox, never()).enqueueSmsEvent(any(), eq(WebhookEventType.SMS_FAILED), any());
    }

    @Test
    void finalizeOne_failure_backoff_doubles_correctly_for_second_retry() {
        SmsMessage m = queuedSms();
        m.setStatus(SmsStatus.SENT);
        m.setRetryCount(1);        // second attempt (already retried once)
        Instant now = Instant.parse("2026-06-20T10:03:00Z");

        processor.finalizeOne(m, now, fixedRng(1.0));

        // retryCount: 1 → 2, still < maxRetries(3)
        assertThat(m.getRetryCount()).isEqualTo(2);
        assertThat(m.getStatus()).isEqualTo(SmsStatus.QUEUED);

        // backoffSeconds = 2^(retryCount+1) = 2^(2+1) = 8 seconds
        assertThat(m.getNextRetryAt()).isEqualTo(now.plusSeconds(8L));
        verify(metrics).smsRetried();
    }

    // -----------------------------------------------------------------------
    // finalizeOne — retry budget exhausted
    // -----------------------------------------------------------------------

    @Test
    void finalizeOne_failure_exhausted_sets_FAILED_with_null_nextRetryAt() {
        SmsMessage m = queuedSms();
        m.setStatus(SmsStatus.SENT);
        m.setRetryCount(2);        // one more failure will reach maxRetries=3
        Instant now = Instant.parse("2026-06-20T10:04:00Z");

        processor.finalizeOne(m, now, fixedRng(1.0));

        // retryCount: 2 → 3, equals maxRetries(3) → terminal FAILED
        assertThat(m.getRetryCount()).isEqualTo(3);
        assertThat(m.getStatus()).isEqualTo(SmsStatus.FAILED);
        assertThat(m.getNextRetryAt()).isNull();
        assertThat(m.getErrorCode()).isEqualTo("CARRIER_REJECTED");

        verify(metrics).smsFailedTerminal();
        verify(webhookOutbox).enqueueSmsEvent(eq(m), eq(WebhookEventType.SMS_FAILED), eq(now));

        // Must NOT trigger smsRetried
        verify(metrics, never()).smsRetried();
        verify(webhookOutbox, never()).enqueueSmsEvent(any(), eq(WebhookEventType.SMS_DELIVERED), any());
    }
}
