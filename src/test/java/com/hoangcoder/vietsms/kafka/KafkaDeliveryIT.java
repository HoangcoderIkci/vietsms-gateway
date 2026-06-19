package com.hoangcoder.vietsms.kafka;

import com.hoangcoder.vietsms.security.ApiKeyService;
import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsRepository;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import com.hoangcoder.vietsms.sms.SmsService;
import com.hoangcoder.vietsms.worker.DeliveryWorker;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end IT proving the Kafka delivery pipeline (mode=kafka) drives an SMS
 * to a terminal state (DELIVERED or FAILED) on a real Redpanda broker.
 *
 * Flow:
 *   SmsService.send() → QUEUED + SmsQueuedEvent (AFTER_COMMIT)
 *   → KafkaDeliveryPublisher.onQueued() → topic vietsms.sms.delivery
 *   → KafkaDeliveryConsumer.onMessage() → markSent + finalizeOne + save
 *   → DELIVERED (p=0.95) or retry loop → FAILED after maxRetries=3
 *
 * The scheduled DeliveryWorker is NOT active in this context (mode=kafka).
 *
 * Run with: mvn -Pdocker-it test
 */
@Tag("docker")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class KafkaDeliveryIT {

    @Container
    static RedpandaContainer kafka =
            new RedpandaContainer("redpandadata/redpanda:v24.2.7");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("vietsms.delivery.mode", () -> "kafka");
    }

    @Autowired ApiKeyService apiKeyService;
    @Autowired SmsService smsService;
    @Autowired SmsRepository smsRepository;
    @Autowired ObjectProvider<DeliveryWorker> deliveryWorkerProvider;

    @Test
    void sms_reaches_terminal_state_via_kafka_pipeline() {
        // Assert DeliveryWorker is NOT active in kafka mode
        assertThat(deliveryWorkerProvider.getIfAvailable())
                .as("DeliveryWorker must be absent when vietsms.delivery.mode=kafka")
                .isNull();

        // Issue an API key and send an SMS
        var key = apiKeyService.issue("kafka-it", "kafka-it@example.com", 100).entity();
        SmsMessage msg = smsService.send(
                key.getId(),
                new SendSmsRequest("0987654321", "kafka pipeline test", null));

        assertThat(msg.getStatus()).isEqualTo(SmsStatus.QUEUED);

        // Await terminal state: DELIVERED (p=0.95) or FAILED (retry budget exhausted)
        // 30 s is generous for 1-3 Kafka round-trips + simulated delivery delay (min 100 ms each)
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    SmsMessage fresh = smsRepository.findById(msg.getId()).orElseThrow();
                    assertThat(fresh.getStatus())
                            .as("SMS must reach a terminal state via Kafka pipeline")
                            .isIn(SmsStatus.DELIVERED, SmsStatus.FAILED);
                });

        // Also assert sentAt was set (markSent ran)
        SmsMessage terminal = smsRepository.findById(msg.getId()).orElseThrow();
        assertThat(terminal.getSentAt())
                .as("sentAt must be set after markSent ran")
                .isNotNull();
    }
}
