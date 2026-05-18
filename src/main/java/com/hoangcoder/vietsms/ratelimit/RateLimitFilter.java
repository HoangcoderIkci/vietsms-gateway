package com.hoangcoder.vietsms.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangcoder.vietsms.common.ApiError;
import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.security.ApiKeyPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${vietsms.ratelimit.sms-per-minute:10}")
    private int smsPerMinute;

    @Value("${vietsms.ratelimit.otp-per-minute:5}")
    private int otpPerMinute;

    private final SlidingWindowLimiter limiter;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> ENDPOINT_KEYS = Map.of(
            "POST /v1/sms/send", "sms",
            "POST /v1/otp/send", "otp"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ENDPOINT_KEYS.containsKey(request.getMethod() + " " + request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        ApiKey key = currentApiKey();
        if (key == null) {
            chain.doFilter(request, response);
            return;
        }
        String endpoint = ENDPOINT_KEYS.get(request.getMethod() + " " + request.getRequestURI());
        int limit = "otp".equals(endpoint) ? otpPerMinute : smsPerMinute;
        String bucket = "ep:" + endpoint + ":key:" + key.getId();

        SlidingWindowLimiter.Decision d = limiter.tryAcquire(bucket, limit, Duration.ofMinutes(1));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(d.remaining()));
        if (!d.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
            ApiError body = ApiError.builder()
                    .timestamp(Instant.now())
                    .status(429)
                    .error("RATE_LIMIT_EXCEEDED")
                    .message("Too many requests for endpoint " + endpoint
                            + "; retry after " + d.retryAfterSeconds() + "s")
                    .path(request.getRequestURI())
                    .build();
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        chain.doFilter(request, response);
    }

    private ApiKey currentApiKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyPrincipal p) return p.getApiKey();
        return null;
    }
}
