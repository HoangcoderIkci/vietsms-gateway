package com.hoangcoder.vietsms.kafka;

import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsRepository;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.worker.DeliveryProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kafka consumer for the delivery pipeline (mode=kafka only).
 * Processes one SMS end-to-end per message: QUEUED → SENT → DELIVERED/FAILED.
 * If finalizeOne schedules a retry (status returns to QUEUED), the id is
 * re-published to the topic so another attempt occurs.
 * Termination: once retryCount >= maxRetries, finalizeOne sets FAILED (not QUEUED),
 * so no further re-publish happens.
 */
@Component
@ConditionalOnProperty(name = "vietsms.delivery.mode", havingValue = "kafka")
@RequiredArgsConstructor
@Slf4j
public class KafkaDeliveryConsumer {

    private final SmsRepository repository;
    private final DeliveryProcessor processor;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = KafkaDeliveryPublisher.TOPIC, groupId = "vietsms-delivery")
    @Transactional
    public void onMessage(String idStr) {
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.error("KafkaDeliveryConsumer: invalid id '{}', skipping", idStr);
            return;
        }

        Optional<SmsMessage> opt = repository.findById(id);
        if (opt.isEmpty()) {
            log.warn("KafkaDeliveryConsumer: smsId={} not found, already handled or deleted", id);
            return;
        }

        SmsMessage m = opt.get();
        if (m.getStatus() != SmsStatus.QUEUED) {
            log.debug("KafkaDeliveryConsumer: smsId={} status={} — not QUEUED, skipping", id, m.getStatus());
            return;
        }

        Instant now = Instant.now();
        processor.markSent(m, now);
        processor.finalizeOne(m, now, ThreadLocalRandom.current());
        repository.save(m);

        // If processor scheduled a retry (QUEUED again with budget remaining), re-publish.
        if (m.getStatus() == SmsStatus.QUEUED) {
            log.info("KafkaDeliveryConsumer: smsId={} scheduled for retry (retryCount={}), re-publishing to topic",
                    id, m.getRetryCount());
            kafkaTemplate.send(KafkaDeliveryPublisher.TOPIC, idStr);
        }
    }
}
