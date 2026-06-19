package com.hoangcoder.vietsms.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis-backed distributed rate limiter using Bucket4j + Lettuce.
 * Active only when {@code vietsms.ratelimit.backend=redis}.
 * Two app instances pointing at the same Redis share each bucket's token count.
 */
@Component
@ConditionalOnProperty(name = "vietsms.ratelimit.backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private final String redisHost;
    private final int redisPort;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;
    private LettuceBasedProxyManager<String> proxyManager;

    public RedisRateLimiter(
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    @PostConstruct
    void init() {
        redisClient = RedisClient.create(RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build());
        connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        proxyManager = Bucket4jLettuce.casBasedBuilder(connection).build();
    }

    @PreDestroy
    void destroy() {
        if (connection != null) connection.close();
        if (redisClient != null) redisClient.shutdown();
    }

    @Override
    public Decision tryAcquire(String bucketKey, int limit, Duration window) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, window)
                        .build())
                .build();

        var bucket = proxyManager.builder().build(bucketKey, configSupplier);
        var probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return new Decision(true, 0, (int) probe.getRemainingTokens());
        } else {
            long retryAfterNanos = probe.getNanosToWaitForRefill();
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterNanos / 1_000_000_000.0));
            return new Decision(false, retryAfterSeconds, 0);
        }
    }
}
