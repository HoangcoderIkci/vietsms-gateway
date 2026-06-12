package com.hoangcoder.vietsms.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ObjectMapper objectMapper;

    String rawKey;

    @BeforeEach
    void issueKey() {
        rawKey = apiKeyService.issue("webhook-test-" + System.nanoTime(), "t@example.com", 1000).rawKey();
    }

    // -------------------------------------------------------
    // POST /v1/webhooks — register
    // -------------------------------------------------------

    @Test
    void register_returns_201_with_secret() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook",
                "events", List.of("sms.delivered")
        ));

        mvc.perform(post("/v1/webhooks")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.url", equalTo("https://example.com/hook")))
                .andExpect(jsonPath("$.events", hasItem("sms.delivered")))
                // Secret must be present and exactly 32 hex characters
                .andExpect(jsonPath("$.secret", matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void register_requires_auth() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook",
                "events", List.of("sms.delivered")
        ));

        mvc.perform(post("/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------
    // GET /v1/webhooks — list
    // -------------------------------------------------------

    @Test
    void list_does_not_include_secret() throws Exception {
        // Register first
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook2",
                "events", List.of("sms.sent")
        ));
        mvc.perform(post("/v1/webhooks")
                .header("x-api-key", rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mvc.perform(get("/v1/webhooks").header("x-api-key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url", equalTo("https://example.com/hook2")))
                // secret must NOT appear in list response
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    // -------------------------------------------------------
    // POST /v1/webhooks — limit
    // -------------------------------------------------------

    @Test
    void registering_sixth_endpoint_returns_409() throws Exception {
        // Register 5 endpoints
        for (int i = 0; i < 5; i++) {
            String body = objectMapper.writeValueAsString(Map.of(
                    "url", "https://example.com/hook-limit-" + i,
                    "events", List.of("sms.delivered")
            ));
            mvc.perform(post("/v1/webhooks")
                            .header("x-api-key", rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        // 6th must fail
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook-over-limit",
                "events", List.of("sms.delivered")
        ));
        mvc.perform(post("/v1/webhooks")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", equalTo("WEBHOOK_LIMIT_REACHED")));
    }

    // -------------------------------------------------------
    // POST /v1/webhooks — unknown event name
    // -------------------------------------------------------

    @Test
    void unknown_event_name_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook",
                "events", List.of("sms.teleported")
        ));
        mvc.perform(post("/v1/webhooks")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("VALIDATION_ERROR")));
    }

    // -------------------------------------------------------
    // DELETE /v1/webhooks/{id} — other key's endpoint returns 404
    // -------------------------------------------------------

    @Test
    void delete_other_keys_endpoint_returns_404() throws Exception {
        // Register endpoint under key A (rawKey)
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook-other",
                "events", List.of("sms.sent")
        ));
        String response = mvc.perform(post("/v1/webhooks")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long endpointId = objectMapper.readTree(response).get("id").asLong();

        // Create a second API key
        String otherKey = apiKeyService.issue("other-" + System.nanoTime(), "o@example.com", 1000).rawKey();

        // Try to delete with the other key — must return 404
        mvc.perform(delete("/v1/webhooks/" + endpointId)
                        .header("x-api-key", otherKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("NOT_FOUND")));
    }

    // -------------------------------------------------------
    // GET /v1/webhooks/{id}/deliveries — missing status returns 400
    // -------------------------------------------------------

    @Test
    void deliveries_without_status_returns_400() throws Exception {
        // Register an endpoint first
        String body = objectMapper.writeValueAsString(Map.of(
                "url", "https://example.com/hook-deliveries",
                "events", List.of("sms.failed")
        ));
        String response = mvc.perform(post("/v1/webhooks")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long endpointId = objectMapper.readTree(response).get("id").asLong();

        // Call without status param
        mvc.perform(get("/v1/webhooks/" + endpointId + "/deliveries")
                        .header("x-api-key", rawKey))
                .andExpect(status().isBadRequest());
    }
}
