package com.hoangcoder.vietsms.sms;

/**
 * Domain event published after a new SmsMessage is persisted (QUEUED).
 * Consumed by KafkaDeliveryPublisher when mode=kafka; harmless when no listener
 * is registered (scheduled mode).
 */
public record SmsQueuedEvent(Long smsId) {}
