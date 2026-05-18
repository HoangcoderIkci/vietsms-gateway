package com.hoangcoder.vietsms.worker;

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
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryWorkerTest {

    @Autowired SmsService smsService;
    @Autowired SmsRepository smsRepository;
    @Autowired ApiKeyService apiKeyService;
    @Autowired DeliveryWorker worker;

    @Test
    void queued_message_eventually_reaches_terminal_state() {
        var key = apiKeyService.issue("worker-test", "t@example.com", 100).entity();
        SmsMessage saved = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "hello", null));

        assertThat(saved.getStatus()).isEqualTo(SmsStatus.QUEUED);

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    SmsMessage fresh = smsRepository.findById(saved.getId()).orElseThrow();
                    assertThat(fresh.getStatus())
                            .isIn(SmsStatus.DELIVERED, SmsStatus.FAILED);
                });
    }

    @Test
    void tick_pickup_transitions_queued_to_sent() {
        var key = apiKeyService.issue("worker-pick", "t@example.com", 100).entity();
        SmsMessage saved = smsService.send(key.getId(),
                new SendSmsRequest("0987654321", "hello", null));
        Long id = saved.getId();

        worker.pickQueued(java.time.Instant.now());

        SmsMessage fresh = smsRepository.findById(id).orElseThrow();
        assertThat(fresh.getStatus()).isIn(SmsStatus.SENT, SmsStatus.DELIVERED, SmsStatus.FAILED);
        assertThat(fresh.getSentAt()).isNotNull();
    }
}
