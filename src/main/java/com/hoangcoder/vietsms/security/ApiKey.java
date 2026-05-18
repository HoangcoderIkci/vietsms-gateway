package com.hoangcoder.vietsms.security;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_prefix", nullable = false, unique = true, length = 8)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 60)
    private String keyHash;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "owner_email", length = 128)
    private String ownerEmail;

    @Column(name = "rate_limit_rpm", nullable = false)
    private Integer rateLimitRpm;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
