package com.hoangcoder.vietsms.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class VietsmsMetrics {

    private final Counter smsEnqueued;
    private final Counter smsDelivered;
    private final Counter smsFailedTerminal;
    private final Counter smsRetried;
    private final Counter otpIssued;
    private final Counter otpVerified;
    private final Counter otpInvalid;
    private final Counter otpLocked;
    private final Counter rateLimitTripped;
    private final Counter webhookDelivered;
    private final Counter webhookFailed;
    private final Counter webhookDead;
    private final Timer webhookLatency;

    public VietsmsMetrics(MeterRegistry registry) {
        smsEnqueued = Counter.builder("vietsms.sms.enqueued")
                .description("Number of SMS messages accepted by the API and put on the queue")
                .register(registry);
        smsDelivered = Counter.builder("vietsms.sms.delivered")
                .description("Number of SMS messages reaching DELIVERED state")
                .register(registry);
        smsFailedTerminal = Counter.builder("vietsms.sms.failed_terminal")
                .description("Number of SMS messages reaching terminal FAILED state after all retries")
                .register(registry);
        smsRetried = Counter.builder("vietsms.sms.retried")
                .description("Number of SMS retry attempts scheduled")
                .register(registry);
        otpIssued = Counter.builder("vietsms.otp.issued")
                .description("Number of OTP codes issued")
                .register(registry);
        otpVerified = Counter.builder("vietsms.otp.verified")
                .description("Number of OTP codes successfully verified")
                .register(registry);
        otpInvalid = Counter.builder("vietsms.otp.invalid_attempt")
                .description("Number of wrong OTP submissions")
                .register(registry);
        otpLocked = Counter.builder("vietsms.otp.locked")
                .description("Number of OTP codes locked after max attempts")
                .register(registry);
        rateLimitTripped = Counter.builder("vietsms.ratelimit.tripped")
                .description("Number of requests rejected by the rate limiter")
                .register(registry);
        webhookDelivered = Counter.builder("vietsms.webhook.delivered")
                .description("Number of webhook deliveries acknowledged with 2xx")
                .register(registry);
        webhookFailed = Counter.builder("vietsms.webhook.failed_attempt")
                .description("Number of failed webhook delivery attempts (will retry)")
                .register(registry);
        webhookDead = Counter.builder("vietsms.webhook.dead")
                .description("Number of webhook deliveries dead-lettered after max attempts")
                .register(registry);
        webhookLatency = Timer.builder("vietsms.webhook.latency")
                .description("Latency of webhook HTTP delivery attempts")
                .publishPercentileHistogram()  // xuất *_seconds_bucket để Prometheus histogram_quantile (p95) hoạt động
                .register(registry);
    }

    public void smsEnqueued() { smsEnqueued.increment(); }
    public void smsDelivered() { smsDelivered.increment(); }
    public void smsFailedTerminal() { smsFailedTerminal.increment(); }
    public void smsRetried() { smsRetried.increment(); }
    public void otpIssued() { otpIssued.increment(); }
    public void otpVerified() { otpVerified.increment(); }
    public void otpInvalid() { otpInvalid.increment(); }
    public void otpLocked() { otpLocked.increment(); }
    public void rateLimitTripped() { rateLimitTripped.increment(); }
    public void webhookDelivered() { webhookDelivered.increment(); }
    public void webhookFailedAttempt() { webhookFailed.increment(); }
    public void webhookDead() { webhookDead.increment(); }
    public void webhookLatency(Duration d) { webhookLatency.record(d); }
}
