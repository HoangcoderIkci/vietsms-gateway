package com.hoangcoder.vietsms.otp;

import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1 variant: when expose-dev-code=true, devCode IS present in the response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "vietsms.otp.expose-dev-code=true",
        "vietsms.ratelimit.otp-per-phone-cooldown-seconds=0"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtpControllerExposeDevCodeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApiKeyService apiKeyService;

    @Test
    void devCode_is_present_when_expose_dev_code_is_true() throws Exception {
        String rawKey = apiKeyService.issue("otp-ctrl-dev-" + System.nanoTime(), "d@example.com", 1000).rawKey();

        mvc.perform(post("/v1/otp/send")
                        .header("x-api-key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0977654321\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.devCode", notNullValue()));
    }
}
