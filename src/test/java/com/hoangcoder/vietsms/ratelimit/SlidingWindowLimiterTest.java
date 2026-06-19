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

    // -----------------------------------------------------------------------
    // Eviction tests (C5 – memory-leak fix)
    // -----------------------------------------------------------------------

    @Test
    void evictStale_removes_empty_buckets() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant t0 = Instant.parse("2026-05-18T22:00:00Z");

        // Populate several distinct buckets
        limiter.tryAcquire("x", 10, Duration.ofMinutes(1), t0);
        limiter.tryAcquire("y", 10, Duration.ofMinutes(1), t0);
        limiter.tryAcquire("z", 10, Duration.ofMinutes(1), t0);
        assertThat(limiter.size()).isEqualTo(3);

        // Advance clock well past retention (RETENTION = 5 min); all timestamps are stale
        Instant evictAt = t0.plus(SlidingWindowLimiter.RETENTION).plusSeconds(1);
        limiter.evictStale(evictAt);

        assertThat(limiter.size()).isZero();
    }

    @Test
    void evictStale_keeps_active_buckets() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant t0 = Instant.parse("2026-05-18T22:00:00Z");

        limiter.tryAcquire("active", 10, Duration.ofMinutes(1), t0);
        limiter.tryAcquire("stale",  10, Duration.ofMinutes(1), t0);
        assertThat(limiter.size()).isEqualTo(2);

        // "active" gets a fresh request just before eviction time
        Instant evictAt = t0.plus(SlidingWindowLimiter.RETENTION).plusSeconds(1);
        limiter.tryAcquire("active", 10, Duration.ofMinutes(1), evictAt.minusSeconds(1));

        limiter.evictStale(evictAt);

        // Only the stale bucket should have been removed
        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void evictStale_removes_bucket_emptied_by_reset() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant t0 = Instant.parse("2026-05-18T22:00:00Z");

        limiter.tryAcquire("k", 10, Duration.ofMinutes(1), t0);
        assertThat(limiter.size()).isEqualTo(1);

        limiter.reset("k");

        // Evict at any time — the bucket is now empty so should be removed immediately
        limiter.evictStale(t0.plusSeconds(1));

        assertThat(limiter.size()).isZero();
    }

    @Test
    void evictStale_does_not_affect_rate_limiting_decisions() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter();
        Instant t0 = Instant.parse("2026-05-18T22:00:00Z");

        // Fill bucket to limit
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k", 3, Duration.ofMinutes(1), t0.plusSeconds(i));
        }

        // Eviction at t0+10s — timestamps are within retention, bucket must survive
        limiter.evictStale(t0.plusSeconds(10));
        assertThat(limiter.size()).isEqualTo(1);

        // Rate-limit decision must still be BLOCKED (entries still within 1-min window)
        var blocked = limiter.tryAcquire("k", 3, Duration.ofMinutes(1), t0.plusSeconds(10));
        assertThat(blocked.allowed()).isFalse();
    }
}
