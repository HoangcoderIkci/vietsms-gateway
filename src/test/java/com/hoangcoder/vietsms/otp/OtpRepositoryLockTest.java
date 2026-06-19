package com.hoangcoder.vietsms.otp;

import com.hoangcoder.vietsms.security.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C4 (correctness): proves that findActiveForUpdate with PESSIMISTIC_WRITE lock
 * compiles, executes, and returns the active OTP for a phone.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtpRepositoryLockTest {

    @Autowired
    OtpRepository otpRepository;

    @Autowired
    ApiKeyService apiKeyService;

    @Test
    @Transactional
    void findActiveForUpdate_returns_active_otp_with_lock() {
        Long apiKeyId = apiKeyService.issue("lock-test-" + System.nanoTime(), "t@example.com", 100)
                .entity().getId();
        Instant now = Instant.now();

        OtpCode saved = otpRepository.save(OtpCode.builder()
                .apiKeyId(apiKeyId)
                .phone("+84900000001")
                .codeHash("$2a$10$dummy_hash_for_lock_test_only_xxxxxx")
                .attempts(0)
                .maxAttempts(3)
                .expiresAt(now.plusSeconds(300))
                .locked(false)
                .createdAt(now)
                .build());

        List<OtpCode> result = otpRepository.findActiveForUpdate("+84900000001", now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(saved.getId());
        assertThat(result.get(0).getPhone()).isEqualTo("+84900000001");
        assertThat(result.get(0).getVerifiedAt()).isNull();
        assertThat(result.get(0).getLocked()).isFalse();
    }

    @Test
    @Transactional
    void findActiveForUpdate_excludes_expired_otp() {
        Long apiKeyId = apiKeyService.issue("lock-test-exp-" + System.nanoTime(), "t@example.com", 100)
                .entity().getId();
        Instant now = Instant.now();

        otpRepository.save(OtpCode.builder()
                .apiKeyId(apiKeyId)
                .phone("+84900000002")
                .codeHash("$2a$10$dummy_hash_for_expiry_test_xxxxxxx")
                .attempts(0)
                .maxAttempts(3)
                .expiresAt(now.minusSeconds(10))  // already expired
                .locked(false)
                .createdAt(now.minusSeconds(310))
                .build());

        List<OtpCode> result = otpRepository.findActiveForUpdate("+84900000002", now);

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void findActiveForUpdate_excludes_locked_otp() {
        Long apiKeyId = apiKeyService.issue("lock-test-lck-" + System.nanoTime(), "t@example.com", 100)
                .entity().getId();
        Instant now = Instant.now();

        otpRepository.save(OtpCode.builder()
                .apiKeyId(apiKeyId)
                .phone("+84900000003")
                .codeHash("$2a$10$dummy_hash_for_locked_test_xxxxxxx")
                .attempts(3)
                .maxAttempts(3)
                .expiresAt(now.plusSeconds(300))
                .locked(true)  // locked
                .createdAt(now)
                .build());

        List<OtpCode> result = otpRepository.findActiveForUpdate("+84900000003", now);

        assertThat(result).isEmpty();
    }
}
