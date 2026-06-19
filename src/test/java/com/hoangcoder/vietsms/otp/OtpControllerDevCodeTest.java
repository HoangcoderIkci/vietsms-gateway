package com.hoangcoder.vietsms.otp;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

/**
 * C1 (security): verifies devCode gating via vietsms.otp.expose-dev-code property.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.ratelimit.otp-per-phone-cooldown-seconds=0"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtpControllerDevCodeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ObjectMapper objectMapper;

    String rawKey;

    @BeforeEach
    void issueKey() {
        rawKey = apiKeyService.issue("otp-ctrl-test-" + System.nanoTime(), "t@example.com", 1000).rawKey();
    }

    /**
     * Without expose-dev-code=true (default false), devCode must be absent (null / omitted).
     * The @JsonInclude(NON_NULL) on SendOtpResponse means null fields are omitted entirely.
     */
    @Test
    void devCode_is_absent_when_expose_dev_code_is_false() throws Exception {
        mvc.perform(post("/v1/otp/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0987654321\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.devCode").doesNotExist());
    }

}
