package com.hoangcoder.vietsms.sms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SmsRepository extends JpaRepository<SmsMessage, Long> {
    Optional<SmsMessage> findByApiKeyIdAndClientMessageId(Long apiKeyId, String clientMessageId);

    Page<SmsMessage> findByApiKeyIdOrderByCreatedAtDesc(Long apiKeyId, Pageable pageable);

    List<SmsMessage> findTop50ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
            SmsStatus status, Instant now);

    List<SmsMessage> findTop50ByStatusOrderByIdAsc(SmsStatus status);
}
