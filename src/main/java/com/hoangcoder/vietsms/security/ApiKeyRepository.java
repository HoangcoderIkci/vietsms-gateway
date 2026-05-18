package com.hoangcoder.vietsms.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);

    long countByActiveTrue();
}
