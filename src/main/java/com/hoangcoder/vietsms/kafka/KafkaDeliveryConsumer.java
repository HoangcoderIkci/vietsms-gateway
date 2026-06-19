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
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kafka consumer for the delivery pipeline (mode=kafka only).
 * Processes one SMS end-to-end per message: QUEUED → SENT → DELIVERED/FAILED.
 *
 * <p><b>Retry / backoff design:</b><br>
 * When finalizeOne schedules a retry (status returns to QUEUED with nextRetryAt set),
 * the id is re-published to the topic only at {@code nextRetryAt} via a TaskScheduler,
 * so the exponential backoff computed by DeliveryProcessor is actually honored.
 * Immediate re-publish is intentionally avoided to prevent hot-looping the broker.
 *
 * <p><b>Simplification vs. production Kafka patterns:</b><br>
 * A production system would use a delay-topic (or DLQ + retry-topic) so retries
 * survive application restart. Here we rely on an in-process TaskScheduler: if the
 * app restarts between scheduling and firing, the retry is lost.  The persisted
 * {@code nextRetryAt} field means the durable scheduled-worker path (DeliveryWorker)
 * can recover these if the app is restarted with mode=worker; that is the intended
 * fallback for this scope.
 *
 * <p>Termination: once retryCount >= maxRetries, finalizeOne sets FAILED (not QUEUED),
 * so no reschedule ever happens in that path.
 */
@Component
@ConditionalOnProperty(name = "vietsms.delivery.mode", havingValue = "kafka")
@RequiredArgsConstructor
@Slf4j
public class KafkaDeliveryConsumer {

    private final SmsRepository repository;
    private final DeliveryProcessor processor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TaskScheduler kafkaRetryTaskScheduler;

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

        // If the message has a future nextRetryAt it was re-published early (e.g. from a
        // previous consumer restart). Defer it again rather than processing ahead of schedule.
        Instant now = Instant.now();
        if (m.getStatus() == SmsStatus.QUEUED
                && m.getNextRetryAt() != null
                && m.getNextRetryAt().isAfter(now)) {
            Instant retryAt = m.getNextRetryAt();
            log.info("KafkaDeliveryConsumer: smsId={} not yet due (nextRetryAt={}), scheduling deferred re-publish",
                    id, retryAt);
            // schedule() is non-blocking — runs on the kafkaRetryTaskScheduler thread pool
            kafkaRetryTaskScheduler.schedule(
                    () -> kafkaTemplate.send(KafkaDeliveryPublisher.TOPIC, idStr),
                    retryAt);
            return;
        }

        if (m.getStatus() != SmsStatus.QUEUED) {
            log.debug("KafkaDeliveryConsumer: smsId={} status={} — not QUEUED, skipping", id, m.getStatus());
            return;
        }

        processor.markSent(m, now);
        processor.finalizeOne(m, now, ThreadLocalRandom.current());
        repository.save(m);

        // If processor scheduled a retry (QUEUED again with budget remaining),
        // defer the re-publish to nextRetryAt via TaskScheduler — NOT immediately.
        // This honours the exponential backoff and prevents hot-looping the broker.
        if (m.getStatus() == SmsStatus.QUEUED) {
            Instant retryAt = m.getNextRetryAt();
            log.info("KafkaDeliveryConsumer: smsId={} retry scheduled at {} (retryCount={}), deferring re-publish",
                    id, retryAt, m.getRetryCount());
            kafkaRetryTaskScheduler.schedule(
                    () -> kafkaTemplate.send(KafkaDeliveryPublisher.TOPIC, idStr),
                    retryAt);
        }
    }
}
