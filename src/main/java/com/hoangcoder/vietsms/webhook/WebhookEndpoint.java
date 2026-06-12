package com.hoangcoder.vietsms.webhook;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "webhook_endpoint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 64)
    private String secret;

    /** Comma-separated wire names, e.g. "sms.sent,sms.failed" */
    @Column(nullable = false, length = 255)
    private String events;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Parses the CSV events column into a typed set. */
    public Set<WebhookEventType> getEventSet() {
        if (events == null || events.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(events.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(WebhookEventType::fromWire)
                .collect(Collectors.toSet());
    }
}
