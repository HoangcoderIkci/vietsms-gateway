package com.hoangcoder.vietsms.kafka;

import com.hoangcoder.vietsms.sms.SmsMessage;
import com.hoangcoder.vietsms.sms.SmsRepository;
import com.hoangcoder.vietsms.sms.SmsStatus;
import com.hoangcoder.vietsms.worker.DeliveryProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaDeliveryConsumer retry-backoff logic (Defect C3 fix).
 *
 * <p>Uses a minimal stub of DeliveryProcessor that manipulates SmsMessage directly,
 * so no Spring context is needed and no real Kafka broker is required.
 */
@ExtendWith(MockitoExtension.class)
class KafkaDeliveryConsumerTest {

    @Mock SmsRepository repository;
    @Mock DeliveryProcessor processor;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @Mock TaskScheduler kafkaRetryTaskScheduler;

    KafkaDeliveryConsumer consumer;

    @BeforeEach
    void setup() {
        consumer = new KafkaDeliveryConsumer(repository, processor, kafkaTemplate, kafkaRetryTaskScheduler);
    }

    // -----------------------------------------------------------------------
    // Helper builders
    // -----------------------------------------------------------------------

    private SmsMessage queuedSms(Long id) {
        return SmsMessage.builder()
                .id(id)
                .apiKeyId(1L)
                .toPhone("0987654321")
                .content("test")
                .status(SmsStatus.QUEUED)
                .retryCount(0)
                .nextRetryAt(null)
                .createdAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // Case 1: message consumed, delivery fails → processor leaves status=QUEUED
    // with nextRetryAt in the future (retry scheduled).
    // Expected: NO immediate kafkaTemplate.send(), YES taskScheduler.schedule()
    // at the exact nextRetryAt instant.
    // -----------------------------------------------------------------------
    @Test
    void when_processor_schedules_retry_should_defer_republish_via_scheduler_not_immediately() {
        SmsMessage sms = queuedSms(42L);
        when(repository.findById(42L)).thenReturn(Optional.of(sms));

        // Simulate DeliveryProcessor.finalizeOne leaving sms in QUEUED + nextRetryAt
        Instant futureRetryAt = Instant.now().plusSeconds(4);
        doAnswer(inv -> {
            sms.setStatus(SmsStatus.QUEUED);
            sms.setRetryCount(1);
            sms.setNextRetryAt(futureRetryAt);
            sms.setSentAt(null);
            return null;
        }).when(processor).finalizeOne(eq(sms), any(Instant.class), any(Random.class));

        consumer.onMessage("42");

        // Must NOT call kafkaTemplate.send() immediately
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class));

        // Must call taskScheduler.schedule() with the exact futureRetryAt instant
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(kafkaRetryTaskScheduler, times(1))
                .schedule(runnableCaptor.capture(), instantCaptor.capture());

        assertThat(instantCaptor.getValue())
                .as("Scheduled instant must equal nextRetryAt")
                .isEqualTo(futureRetryAt);

        // When the scheduled runnable fires it should republish to the topic
        runnableCaptor.getValue().run();
        verify(kafkaTemplate, times(1)).send(KafkaDeliveryPublisher.TOPIC, "42");
    }

    // -----------------------------------------------------------------------
    // Case 2: message consumed, delivery succeeds → processor sets DELIVERED.
    // Expected: NO reschedule, NO immediate republish.
    // -----------------------------------------------------------------------
    @Test
    void when_processor_delivers_successfully_should_not_reschedule_or_republish() {
        SmsMessage sms = queuedSms(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(sms));

        doAnswer(inv -> {
            sms.setStatus(SmsStatus.DELIVERED);
            sms.setDeliveredAt(Instant.now());
            return null;
        }).when(processor).finalizeOne(eq(sms), any(Instant.class), any(Random.class));

        consumer.onMessage("7");

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class));
        verify(kafkaRetryTaskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    // -----------------------------------------------------------------------
    // Case 3: processor exhausts retries → sets FAILED.
    // Expected: NO reschedule, NO republish.
    // -----------------------------------------------------------------------
    @Test
    void when_processor_reaches_max_retries_and_sets_failed_should_not_reschedule() {
        SmsMessage sms = queuedSms(99L);
        when(repository.findById(99L)).thenReturn(Optional.of(sms));

        doAnswer(inv -> {
            sms.setStatus(SmsStatus.FAILED);
            sms.setRetryCount(3);
            sms.setNextRetryAt(null);
            return null;
        }).when(processor).finalizeOne(eq(sms), any(Instant.class), any(Random.class));

        consumer.onMessage("99");

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class));
        verify(kafkaRetryTaskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    // -----------------------------------------------------------------------
    // Case 4: message arrives in QUEUED but nextRetryAt is still in the future
    // (e.g. re-published early after a consumer restart).
    // Expected: NO processing (markSent not called), deferred re-publish scheduled.
    // -----------------------------------------------------------------------
    @Test
    void when_message_not_yet_due_should_defer_without_processing() {
        SmsMessage sms = queuedSms(55L);
        Instant futureRetryAt = Instant.now().plusSeconds(30);
        sms.setNextRetryAt(futureRetryAt);
        sms.setRetryCount(1);

        when(repository.findById(55L)).thenReturn(Optional.of(sms));

        consumer.onMessage("55");

        // Must NOT process the message
        verify(processor, never()).markSent(any(), any());
        verify(processor, never()).finalizeOne(any(), any(), any());
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class));

        // Must schedule a deferred re-publish at futureRetryAt
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(kafkaRetryTaskScheduler, times(1))
                .schedule(any(Runnable.class), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isEqualTo(futureRetryAt);
    }

    // -----------------------------------------------------------------------
    // Case 5: invalid id string → skipped cleanly, no interactions.
    // -----------------------------------------------------------------------
    @Test
    void invalid_id_string_is_skipped() {
        consumer.onMessage("not-a-number");

        verifyNoInteractions(repository, processor, kafkaTemplate, kafkaRetryTaskScheduler);
    }

    // -----------------------------------------------------------------------
    // Case 6: sms not found in repository → skipped cleanly.
    // -----------------------------------------------------------------------
    @Test
    void unknown_sms_id_is_skipped() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        consumer.onMessage("999");

        verifyNoInteractions(processor, kafkaTemplate, kafkaRetryTaskScheduler);
    }
}
