package com.hoangcoder.vietsms.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditRepository repository;

    @Async
    public void record(Long apiKeyId, String method, String endpoint,
                       int statusCode, String phoneMasked, String requestId) {
        try {
            AuditLog row = AuditLog.builder()
                    .apiKeyId(apiKeyId)
                    .endpoint(endpoint)
                    .method(method)
                    .statusCode(statusCode)
                    .phoneMasked(phoneMasked)
                    .requestId(requestId)
                    .createdAt(Instant.now())
                    .build();
            repository.save(row);
        } catch (Exception e) {
            log.warn("Audit write failed for {} {}: {}", method, endpoint, e.getMessage());
        }
    }
}
