package com.hoangcoder.vietsms.otp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpCode, Long> {

    @Query("""
            select o from OtpCode o
            where o.phone = :phone
              and o.verifiedAt is null
              and o.locked = false
              and o.expiresAt > :now
            order by o.createdAt desc
            limit 1
            """)
    Optional<OtpCode> findActiveByPhone(@Param("phone") String phone, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from OtpCode o
            where o.phone = :phone
              and o.verifiedAt is null
              and o.locked = false
              and o.expiresAt > :now
            order by o.createdAt desc
            """)
    List<OtpCode> findActiveForUpdate(@Param("phone") String phone, @Param("now") Instant now);

    Optional<OtpCode> findTopByPhoneOrderByCreatedAtDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
