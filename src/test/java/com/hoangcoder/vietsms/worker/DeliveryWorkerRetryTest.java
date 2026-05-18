package com.hoangcoder.vietsms.worker;

import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.security.ApiKeyService;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsRepository;
import com.hoangcoder.vietsms.sms.SmsService;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.delivery.worker-interval-ms=200",
        "vietsms.delivery.min-delay-ms=50",
        "vietsms.delivery.success-rate=0.0",
        "vietsms.delivery.max-retries=2"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryWorkerRetryTest {

    @Autowired SmsService smsService;
    @Autowired SmsRepository repository;
    @Autowired ApiKeyService apiKeyService;

    @Test
    void exhausting_retry_budget_lands_in_terminal_failed() {
        ApiKey key = apiKeyService.issue("retry-test", "t@example.com", 100).entity();
        SmsMessage saved = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "will fail", null));

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    SmsMessage fresh = repository.findById(saved.getId()).orElseThrow();
                    assertThat(fresh.getStatus()).isEqualTo(SmsStatus.FAILED);
                    assertThat(fresh.getRetryCount()).isEqualTo(2);
                    assertThat(fresh.getErrorCode()).isEqualTo("CARRIER_REJECTED");
                });
    }
}
