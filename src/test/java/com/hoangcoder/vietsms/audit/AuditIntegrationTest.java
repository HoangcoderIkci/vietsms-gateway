package com.hoangcoder.vietsms.audit;

import com.hoangcoder.vietsms.security.ApiKeyService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AuditRepository auditRepository;
    @Autowired ApiKeyService apiKeyService;

    @Test
    void successful_request_writes_audit_row_with_request_id() throws Exception {
        long before = auditRepository.count();
        String rawKey = apiKeyService.issue("audit-test", "t@example.com", 1000).rawKey();

        MvcResult result = mvc.perform(get("/v1/sms").header("x-api-key", rawKey))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(auditRepository.count()).isGreaterThan(before);
                    List<AuditLog> rows = auditRepository.findAll();
                    AuditLog match = rows.stream()
                            .filter(r -> requestId.equals(r.getRequestId()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "no audit row for request id " + requestId));
                    assertThat(match.getMethod()).isEqualTo("GET");
                    assertThat(match.getEndpoint()).isEqualTo("/v1/sms");
                    assertThat(match.getStatusCode()).isEqualTo(200);
                    assertThat(match.getApiKeyId()).isNotNull();
                });
    }

}
