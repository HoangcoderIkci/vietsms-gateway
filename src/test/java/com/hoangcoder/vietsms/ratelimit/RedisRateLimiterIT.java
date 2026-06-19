package com.hoangcoder.vietsms.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving that RedisRateLimiter enforces a shared distributed limit
 * across two "app instances" (two RedisRateLimiter objects) pointing at the same Redis.
 *
 * Run with: mvn -Pdocker-it test
 */
@Tag("docker")
@Testcontainers
class RedisRateLimiterIT {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedisRateLimiter instance1;
    private RedisRateLimiter instance2;

    @BeforeEach
    void setUp() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);
        instance1 = new RedisRateLimiter(host, port);
        instance1.init();
        instance2 = new RedisRateLimiter(host, port);
        instance2.init();
    }

    @AfterEach
    void tearDown() {
        instance1.destroy();
        instance2.destroy();
    }

    @Test
    void distributed_limit_shared_across_two_instances() {
        // limit=5 per 1-minute window; both instances share the same Redis bucket
        int limit = 5;
        Duration window = Duration.ofMinutes(1);
        String bucketKey = "test:distributed:key:1";

        // Fire 5 requests across both instances (simulating distributed load)
        // Instance1 handles 3, instance2 handles 2 — total 5 = exactly the limit
        RateLimiter.Decision d;

        d = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(4);

        d = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(3);

        d = instance2.tryAcquire(bucketKey, limit, window);
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(2);

        d = instance2.tryAcquire(bucketKey, limit, window);
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(1);

        d = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(0);

        // 6th request — must be DENIED regardless of which instance handles it
        // An in-memory limiter would allow 5 per-instance (total 10); Redis-backed allows 5 total.
        RateLimiter.Decision denied = instance2.tryAcquire(bucketKey, limit, window);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
        assertThat(denied.remaining()).isEqualTo(0);
    }

    @Test
    void remaining_decrements_monotonically_on_single_instance() {
        String bucketKey = "test:remaining:key:2";
        int limit = 3;
        Duration window = Duration.ofMinutes(1);

        RateLimiter.Decision d1 = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d1.allowed()).isTrue();
        assertThat(d1.remaining()).isEqualTo(2);

        RateLimiter.Decision d2 = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d2.allowed()).isTrue();
        assertThat(d2.remaining()).isEqualTo(1);

        RateLimiter.Decision d3 = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d3.allowed()).isTrue();
        assertThat(d3.remaining()).isEqualTo(0);

        // 4th is denied; retryAfter >= 1
        RateLimiter.Decision d4 = instance1.tryAcquire(bucketKey, limit, window);
        assertThat(d4.allowed()).isFalse();
        assertThat(d4.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
    }
}
