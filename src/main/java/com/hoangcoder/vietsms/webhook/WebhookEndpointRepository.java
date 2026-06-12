package com.hoangcoder.vietsms.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {

    List<WebhookEndpoint> findByApiKeyIdAndEnabledTrue(Long apiKeyId);

    long countByApiKeyId(Long apiKeyId);
}
