package com.hoangcoder.vietsms.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window counter. Each bucket holds the timestamps of requests
 * that fell within its window. Concurrent access is serialized per bucket via
 * synchronized on the bucket's deque.
 *
 * <p>Stale buckets (empty or idle for more than {@code RETENTION} minutes) are
 * periodically evicted by {@link #evictStale()} to prevent unbounded map growth.
 */
@Component
@ConditionalOnProperty(name = "vietsms.ratelimit.backend", havingValue = "memory", matchIfMissing = true)
public class SlidingWindowLimiter implements RateLimiter {

    /** Buckets idle longer than this are eligible for eviction. */
    static final Duration RETENTION = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    @Override
    public Decision tryAcquire(String bucketKey, int limit, Duration window) {
        return tryAcquire(bucketKey, limit, window, Instant.now());
    }

    public Decision tryAcquire(String bucketKey, int limit, Duration window, Instant now) {
        Deque<Instant> bucket = buckets.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant cutoff = now.minus(window);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
                bucket.pollFirst();
            }
            if (bucket.size() >= limit) {
                Instant oldest = bucket.peekFirst();
                long retry = window.minus(Duration.between(oldest, now)).getSeconds();
                if (retry < 1) retry = 1;
                return new Decision(false, retry, 0);
            }
            bucket.addLast(now);
            return new Decision(true, 0, limit - bucket.size());
        }
    }

    public void reset(String bucketKey) {
        Deque<Instant> bucket = buckets.get(bucketKey);
        if (bucket != null) {
            synchronized (bucket) {
                bucket.clear();
            }
        }
    }

    public int size() {
        return buckets.size();
    }

    /**
     * Scheduled sweep that removes buckets which are either empty or whose newest
     * timestamp is older than {@link #RETENTION}. The check is done under the
     * bucket's own monitor so it cannot race with an in-flight {@link #tryAcquire}.
     */
    @Scheduled(fixedDelayString = "${vietsms.ratelimit.cleanup-interval-ms:60000}")
    public void evictStale() {
        evictStale(Instant.now());
    }

    /**
     * Package-private overload that accepts an explicit {@code now} for deterministic
     * testing (mirrors the {@link #tryAcquire(String, int, Duration, Instant)} pattern).
     */
    void evictStale(Instant now) {
        Instant retentionCutoff = now.minus(RETENTION);
        buckets.entrySet().removeIf(entry -> {
            Deque<Instant> bucket = entry.getValue();
            synchronized (bucket) {
                // Empty bucket or newest timestamp older than retention → evict
                return bucket.isEmpty() || bucket.peekLast().isBefore(retentionCutoff);
            }
        });
    }
}
