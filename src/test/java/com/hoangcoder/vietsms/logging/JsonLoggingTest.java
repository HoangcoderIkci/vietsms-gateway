package com.hoangcoder.vietsms.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for structured JSON logging via LogstashEncoder.
 *
 * Tests that:
 * 1. The encoder produces valid JSON output.
 * 2. The MDC field "requestId" is included in the JSON output.
 * 3. The log message is present in the JSON output.
 *
 * No Spring context is loaded — the encoder is instantiated directly for
 * deterministic, fast verification independent of Spring profiles.
 */
class JsonLoggingTest {

    /** The MDC key used by AuditFilter (see AuditFilter.MDC_REQUEST_ID). */
    private static final String MDC_REQUEST_ID = "requestId";

    private static final String TEST_REQUEST_ID = "test-req-123";
    private static final String TEST_MESSAGE = "hello structured logging";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MDC.put(MDC_REQUEST_ID, TEST_REQUEST_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(MDC_REQUEST_ID);
    }

    @Test
    void logstashEncoder_produceValidJson_withRequestIdMdc() throws Exception {
        // Arrange: set up a LogstashEncoder with a LoggerContext
        LoggerContext loggerContext = new LoggerContext();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(loggerContext);
        encoder.start();

        // Build a LoggingEvent that captures current MDC state
        Logger logger = loggerContext.getLogger(JsonLoggingTest.class);
        LoggingEvent event = new LoggingEvent(
                JsonLoggingTest.class.getName(),
                logger,
                Level.INFO,
                TEST_MESSAGE,
                null,
                null
        );
        // Inject the MDC map so it's available even outside the MDC thread-local
        event.setMDCPropertyMap(MDC.getCopyOfContextMap());

        // Act: encode the event to bytes
        byte[] encoded = encoder.encode(event);
        encoder.stop();

        // Assert: valid JSON
        assertThat(encoded).isNotEmpty();
        JsonNode json = objectMapper.readTree(encoded);
        assertThat(json).isNotNull();

        // Assert: message field present
        assertThat(json.has("message"))
                .as("JSON output must contain a 'message' field")
                .isTrue();
        assertThat(json.get("message").asText())
                .isEqualTo(TEST_MESSAGE);

        // Assert: requestId MDC field present with the correct value
        assertThat(json.has(MDC_REQUEST_ID))
                .as("JSON output must contain the '" + MDC_REQUEST_ID + "' MDC field")
                .isTrue();
        assertThat(json.get(MDC_REQUEST_ID).asText())
                .isEqualTo(TEST_REQUEST_ID);
    }
}
