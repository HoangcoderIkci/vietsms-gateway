package com.hoangcoder.vietsms.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that /actuator/prometheus is reachable without authentication
 * and exposes vietsms_ custom metrics — proving the Prometheus scrape target works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusEndpointIT {

    @Autowired
    MockMvc mvc;

    @Test
    void prometheus_endpoint_is_accessible_without_api_key_and_exposes_vietsms_metrics() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        // Custom application metrics must appear on the scrape endpoint
        assertThat(body).contains("vietsms_sms");
        assertThat(body).contains("vietsms_webhook");
    }
}
