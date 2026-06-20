package com.hoangcoder.vietsms.ratelimit;

import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that a key with rateLimitRpm=3 is throttled at 3 even when
 * the global sms-per-minute=10, i.e. per-key ceiling is honored (M1 fix).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.ratelimit.sms-per-minute=10"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PerKeyRateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired ApiKeyService apiKeyService;

    String rawKey;

    @BeforeEach
    void issueKey() {
        rawKey = apiKeyService.issue("low-rpm-" + System.nanoTime(), "l@e.com", 3).rawKey();
    }

    @Test
    void fourth_request_throttled_at_per_key_rpm_3_not_global_10() throws Exception {
        String body = "{\"to\":\"0987654321\",\"content\":\"hi\"}";

        // First 3 requests must pass
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/sms/send")
                            .header("x-api-key", rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted());
        }

        // 4th request must be rejected — limited by key's rpm=3, not global 10
        mvc.perform(post("/v1/sms/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", equalTo("3")))
                .andExpect(jsonPath("$.error", equalTo("RATE_LIMIT_EXCEEDED")));
    }
}
