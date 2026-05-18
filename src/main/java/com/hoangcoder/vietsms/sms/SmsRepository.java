package com.hoangcoder.vietsms.sms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SmsRepository extends JpaRepository<SmsMessage, Long> {

    Optional<SmsMessage> findByApiKeyIdAndClientMessageId(Long apiKeyId, String clientMessageId);

    Optional<SmsMessage> findByIdAndApiKeyId(Long id, Long apiKeyId);

    Page<SmsMessage> findByApiKeyIdOrderByCreatedAtDesc(Long apiKeyId, Pageable pageable);

    Page<SmsMessage> findByApiKeyIdAndStatusOrderByCreatedAtDesc(
            Long apiKeyId, SmsStatus status, Pageable pageable);

    @Query("""
            select m from SmsMessage m
            where m.status = :status
              and (m.nextRetryAt is null or m.nextRetryAt <= :now)
            order by m.id asc
            """)
    List<SmsMessage> findReadyForProcessing(
            @Param("status") SmsStatus status,
            @Param("now") Instant now,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select m from SmsMessage m
            where m.status = :status
              and m.sentAt is not null
              and m.sentAt <= :readyBefore
            order by m.id asc
            """)
    List<SmsMessage> findSentReadyToFinalize(
            @Param("status") SmsStatus status,
            @Param("readyBefore") Instant readyBefore,
            org.springframework.data.domain.Pageable pageable);
}
