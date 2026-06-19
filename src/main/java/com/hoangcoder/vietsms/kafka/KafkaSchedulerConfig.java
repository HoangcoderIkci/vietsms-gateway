package com.hoangcoder.vietsms.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Provides a TaskScheduler for the Kafka retry path so deferred re-publishes
 * run off the @KafkaListener thread (non-blocking).
 *
 * Active only when vietsms.delivery.mode=kafka.
 */
@Configuration
@ConditionalOnProperty(name = "vietsms.delivery.mode", havingValue = "kafka")
class KafkaSchedulerConfig {

    @Bean
    ThreadPoolTaskScheduler kafkaRetryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
