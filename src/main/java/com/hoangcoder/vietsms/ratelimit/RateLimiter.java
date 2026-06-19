package com.hoangcoder.vietsms.ratelimit;

import java.time.Duration;

/**
 * Strategy interface for rate limiting. Implementations are selected via
 * {@code vietsms.ratelimit.backend} (default: "memory" → {@link SlidingWindowLimiter}).
 */
public interface RateLimiter {

    record Decision(boolean allowed, long retryAfterSeconds, int remaining) {}

    Decision tryAcquire(String bucketKey, int limit, Duration window);
}
