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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.ratelimit.sms-per-minute=3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateLimitIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ApiKeyService apiKeyService;

    String rawKey;

    @BeforeEach
    void issueKey() {
        rawKey = apiKeyService.issue("rl-test-" + System.nanoTime(), "t@example.com", 1000).rawKey();
    }

    @Test
    void fourth_send_within_minute_returns_429_with_retry_after() throws Exception {
        String body = "{\"to\":\"0987654321\",\"content\":\"hi\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/sms/send")
                            .header("x-api-key", rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/v1/sms/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error", equalTo("RATE_LIMIT_EXCEEDED")));
    }

    @Test
    void rate_limit_headers_present_on_success() throws Exception {
        mvc.perform(post("/v1/sms/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"0987654321\",\"content\":\"hi\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().string("X-Request-Id", notNullValue()));
    }
}
