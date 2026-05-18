package com.hoangcoder.vietsms.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findTopByPhoneAndVerifiedAtIsNullAndLockedFalseOrderByCreatedAtDesc(String phone);

    Optional<OtpCode> findTopByPhoneOrderByCreatedAtDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
