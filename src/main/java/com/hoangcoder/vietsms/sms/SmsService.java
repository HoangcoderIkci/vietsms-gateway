package com.hoangcoder.vietsms.sms;

import com.hoangcoder.vietsms.common.PhoneNormalizer;
import com.hoangcoder.vietsms.common.VietsmsMetrics;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsRepository repository;
    private final VietsmsMetrics metrics;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SmsMessage send(Long apiKeyId, SendSmsRequest request) {
        String normalizedPhone = PhoneNormalizer.normalize(request.to());

        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            Optional<SmsMessage> existing = repository.findByApiKeyIdAndClientMessageId(
                    apiKeyId, request.clientMessageId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        SmsMessage entity = SmsMessage.builder()
                .apiKeyId(apiKeyId)
                .clientMessageId(blankToNull(request.clientMessageId()))
                .toPhone(normalizedPhone)
                .content(request.content())
                .status(SmsStatus.QUEUED)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        try {
            SmsMessage saved = repository.save(entity);
            metrics.smsEnqueued();
            eventPublisher.publishEvent(new SmsQueuedEvent(saved.getId()));
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Lost an idempotency race — return the now-existing record.
            if (request.clientMessageId() != null) {
                return repository.findByApiKeyIdAndClientMessageId(
                                apiKeyId, request.clientMessageId())
                        .orElseThrow(() -> race);
            }
            throw race;
        }
    }

    @Transactional(readOnly = true)
    public Optional<SmsMessage> getById(Long apiKeyId, Long id) {
        return repository.findByIdAndApiKeyId(id, apiKeyId);
    }

    @Transactional(readOnly = true)
    public Page<SmsMessage> list(Long apiKeyId, SmsStatus statusFilter, Pageable pageable) {
        if (statusFilter == null) {
            return repository.findByApiKeyIdOrderByCreatedAtDesc(apiKeyId, pageable);
        }
        return repository.findByApiKeyIdAndStatusOrderByCreatedAtDesc(apiKeyId, statusFilter, pageable);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
