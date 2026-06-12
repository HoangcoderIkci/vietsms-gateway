package com.hoangcoder.vietsms.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findTop50ByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            WebhookDeliveryStatus status, Instant now);

    List<WebhookDelivery> findByEndpointIdAndStatusOrderByCreatedAtDesc(
            Long endpointId, WebhookDeliveryStatus status);

    long deleteByEndpointId(Long endpointId);
}
