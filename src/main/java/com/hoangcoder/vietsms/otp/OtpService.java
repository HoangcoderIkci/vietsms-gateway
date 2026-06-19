package com.hoangcoder.vietsms.otp;

import com.hoangcoder.vietsms.common.PhoneNormalizer;
import com.hoangcoder.vietsms.common.VietsmsMetrics;
import com.hoangcoder.vietsms.common.exceptions.TooEarlyException;
import com.hoangcoder.vietsms.otp.dto.SendOtpRequest;
import com.hoangcoder.vietsms.otp.dto.VerifyOtpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    @Value("${vietsms.otp.default-length:6}")
    private int defaultLength;

    @Value("${vietsms.otp.default-ttl-seconds:300}")
    private int defaultTtlSeconds;

    @Value("${vietsms.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${vietsms.ratelimit.otp-per-phone-cooldown-seconds:30}")
    private long cooldownSeconds;

    private final OtpRepository repository;
    private final VietsmsMetrics metrics;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public record Issued(OtpCode entity, String rawCode) {}

    @Transactional
    public Issued send(Long apiKeyId, SendOtpRequest request) {
        String phone = PhoneNormalizer.normalize(request.phone());
        enforceCooldown(phone);

        int length = request.length() != null ? request.length() : defaultLength;
        int ttl = request.ttlSeconds() != null ? request.ttlSeconds() : defaultTtlSeconds;

        String rawCode = generateNumericCode(length);
        Instant now = Instant.now();

        OtpCode entity = OtpCode.builder()
                .apiKeyId(apiKeyId)
                .phone(phone)
                .codeHash(encoder.encode(rawCode))
                .attempts(0)
                .maxAttempts(maxAttempts)
                .expiresAt(now.plusSeconds(ttl))
                .locked(false)
                .createdAt(now)
                .build();

        Issued issued = new Issued(repository.save(entity), rawCode);
        metrics.otpIssued();
        return issued;
    }

    @Transactional
    public VerifyOtpResponse verify(String phoneInput, String code) {
        String phone = PhoneNormalizer.normalize(phoneInput);
        Instant now = Instant.now();

        List<OtpCode> activeList = repository.findActiveForUpdate(phone, now);
        Optional<OtpCode> activeOpt = activeList.isEmpty() ? Optional.empty() : Optional.of(activeList.get(0));
        if (activeOpt.isEmpty()) {
            Optional<OtpCode> latest = repository.findTopByPhoneOrderByCreatedAtDesc(phone);
            if (latest.isEmpty()) {
                return VerifyOtpResponse.fail("NO_OTP_ISSUED", null);
            }
            OtpCode l = latest.get();
            if (Boolean.TRUE.equals(l.getLocked())) return VerifyOtpResponse.fail("LOCKED", 0);
            if (l.getVerifiedAt() != null) return VerifyOtpResponse.fail("ALREADY_VERIFIED", null);
            if (l.getExpiresAt().isBefore(now)) return VerifyOtpResponse.fail("EXPIRED", null);
            return VerifyOtpResponse.fail("NO_ACTIVE_OTP", null);
        }

        OtpCode active = activeOpt.get();
        if (encoder.matches(code, active.getCodeHash())) {
            active.setVerifiedAt(now);
            repository.save(active);
            metrics.otpVerified();
            return VerifyOtpResponse.ok();
        }

        active.setAttempts(active.getAttempts() + 1);
        int left = active.getMaxAttempts() - active.getAttempts();
        if (left <= 0) {
            active.setLocked(true);
            repository.save(active);
            metrics.otpLocked();
            return VerifyOtpResponse.fail("LOCKED", 0);
        }
        repository.save(active);
        metrics.otpInvalid();
        return VerifyOtpResponse.fail("INVALID_CODE", left);
    }

    private void enforceCooldown(String phone) {
        Instant since = Instant.now().minus(Duration.ofSeconds(cooldownSeconds));
        if (repository.countByPhoneAndCreatedAtAfter(phone, since) > 0) {
            throw new TooEarlyException(
                    "OTP cooldown active for this phone; try again later",
                    cooldownSeconds);
        }
    }

    private String generateNumericCode(int length) {
        long upper = (long) Math.pow(10, length);
        long n = (random.nextLong() & Long.MAX_VALUE) % upper;
        return String.format("%0" + length + "d", n);
    }
}
