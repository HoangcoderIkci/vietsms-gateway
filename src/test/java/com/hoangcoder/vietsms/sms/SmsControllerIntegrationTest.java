package com.hoangcoder.vietsms.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangcoder.vietsms.security.ApiKeyService;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmsControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ObjectMapper objectMapper;

    String rawKey;

    @BeforeEach
    void issueKey() {
        rawKey = apiKeyService.issue("test-" + System.nanoTime(), "t@example.com", 1000).rawKey();
    }

    @Test
    void send_without_key_returns_401() throws Exception {
        mvc.perform(post("/v1/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"0987654321\",\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", equalTo("MISSING_API_KEY")));
    }

    @Test
    void send_with_invalid_phone_returns_400() throws Exception {
        mvc.perform(post("/v1/sms/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"0123456789\",\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void send_valid_returns_202_with_queued_status() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SendSmsRequest("0987654321", "Xin chao", "ctl-test-1"));

        mvc.perform(post("/v1/sms/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("QUEUED")))
                .andExpect(jsonPath("$.to", equalTo("+84987654321")));
    }

    @Test
    void list_returns_only_own_messages() throws Exception {
        // send under own key
        mvc.perform(post("/v1/sms/send")
                .header("x-api-key", rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"0987654321\",\"content\":\"mine\"}"));

        // send under another key
        String otherKey = apiKeyService.issue("other", "x@example.com", 1000).rawKey();
        mvc.perform(post("/v1/sms/send")
                .header("x-api-key", otherKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"0988888888\",\"content\":\"theirs\"}"));

        mvc.perform(get("/v1/sms").header("x-api-key", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content", equalTo("mine")));
    }

    @Test
    void get_unknown_id_returns_404() throws Exception {
        mvc.perform(get("/v1/sms/999999").header("x-api-key", rawKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("NOT_FOUND")));
    }
}
