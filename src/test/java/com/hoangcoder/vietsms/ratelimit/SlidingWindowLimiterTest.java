package com.hoangcoder.vietsms.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterTest {

    @Test
    void allows_up_to_limit_then_blocks() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant t0 = Instant.parse("2026-05-18T22:00:00Z");

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("k", 5, Duration.ofMinutes(1), t0.plusSeconds(i)).allowed())
                    .as("call %d should be allowed", i)
                    .isTrue();
        }
        var blocked = limiter.tryAcquire("k", 5, Duration.ofMinutes(1), t0.plusSeconds(6));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void releases_capacity_when_old_entries_fall_outside_window() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant t0 = Instant.parse("2026-05-18T22:00:00Z");

        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("k", 5, Duration.ofMinutes(1), t0.plusSeconds(i));
        }
        // 61 seconds later, all original entries have aged out
        var d = limiter.tryAcquire("k", 5, Duration.ofMinutes(1), t0.plusSeconds(61));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void independent_buckets_dont_interfere() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant now = Instant.now();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("a", 3, Duration.ofMinutes(1), now.plusMillis(i)).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("a", 3, Duration.ofMinutes(1), now.plusMillis(4)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", 3, Duration.ofMinutes(1), now.plusMillis(5)).allowed()).isTrue();
    }

    @Test
    void reports_remaining_correctly() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant now = Instant.now();
        var first = limiter.tryAcquire("k", 3, Duration.ofMinutes(1), now);
        assertThat(first.remaining()).isEqualTo(2);
        var second = limiter.tryAcquire("k", 3, Duration.ofMinutes(1), now.plusMillis(1));
        assertThat(second.remaining()).isEqualTo(1);
    }
}
