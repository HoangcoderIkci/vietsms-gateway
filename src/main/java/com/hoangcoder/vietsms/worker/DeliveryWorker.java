package com.hoangcoder.vietsms.worker;

import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsRepository;
import com.hoangcoder.vietsms.sms.SmsStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "vietsms.delivery.mode", havingValue = "scheduled", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DeliveryWorker {

    @Value("${vietsms.delivery.batch-size:50}")
    private int batchSize;

    @Value("${vietsms.delivery.min-delay-ms:1000}")
    private long minDelayMs;

    private final SmsRepository repository;
    private final DeliveryProcessor processor;

    @Scheduled(fixedDelayString = "${vietsms.delivery.worker-interval-ms:1000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        try {
            int picked = pickQueued(now);
            int finalized = finalizeSent(now);
            if (picked > 0 || finalized > 0) {
                log.debug("DeliveryWorker tick: picked={} finalized={}", picked, finalized);
            }
        } catch (Exception e) {
            log.error("DeliveryWorker tick failed: {}", e.getMessage(), e);
        }
    }

    int pickQueued(Instant now) {
        List<SmsMessage> ready = repository.findReadyForProcessing(
                SmsStatus.QUEUED, now, PageRequest.of(0, batchSize));
        for (SmsMessage m : ready) {
            processor.markSent(m, now);
        }
        repository.saveAll(ready);
        return ready.size();
    }

    int finalizeSent(Instant now) {
        Instant readyBefore = now.minus(Duration.ofMillis(minDelayMs));
        List<SmsMessage> sent = repository.findSentReadyToFinalize(
                SmsStatus.SENT, readyBefore, PageRequest.of(0, batchSize));
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (SmsMessage m : sent) {
            processor.finalizeOne(m, now, rng);
        }
        repository.saveAll(sent);
        return sent.size();
    }
}
