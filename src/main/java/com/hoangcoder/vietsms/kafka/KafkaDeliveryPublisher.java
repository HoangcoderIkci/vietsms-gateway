package com.hoangcoder.vietsms.kafka;

import com.hoangcoder.vietsms.sms.SmsQueuedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Forwards new-SMS events to the Kafka delivery topic after the DB transaction commits.
 * Active only when vietsms.delivery.mode=kafka.
 */
@Component
@ConditionalOnProperty(name = "vietsms.delivery.mode", havingValue = "kafka")
@RequiredArgsConstructor
public class KafkaDeliveryPublisher {

    static final String TOPIC = "vietsms.sms.delivery";

    private final KafkaTemplate<String, String> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(SmsQueuedEvent event) {
        kafkaTemplate.send(TOPIC, String.valueOf(event.smsId()));
    }
}
