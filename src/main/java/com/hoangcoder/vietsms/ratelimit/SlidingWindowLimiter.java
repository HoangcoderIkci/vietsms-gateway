package com.hoangcoder.vietsms.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 */
@Component
@ConditionalOnProperty(name = "vietsms.ratelimit.backend", havingValue = "memory", matchIfMissing = true)
public class SlidingWindowLimiter implements RateLimiter {

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
}
