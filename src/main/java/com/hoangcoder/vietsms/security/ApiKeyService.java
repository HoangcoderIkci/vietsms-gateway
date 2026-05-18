package com.hoangcoder.vietsms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX_PUBLIC = "vsms_";
    private static final int RANDOM_BYTES = 24;
    private static final int PREFIX_INDEX_LEN = 8;

    private final ApiKeyRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public record IssuedKey(String rawKey, ApiKey entity) {}

    @Transactional
    public IssuedKey issue(String name, String ownerEmail, int rateLimitRpm) {
        byte[] buf = new byte[RANDOM_BYTES];
        random.nextBytes(buf);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        String raw = KEY_PREFIX_PUBLIC + body;
        String prefix = raw.substring(0, PREFIX_INDEX_LEN);
        String hash = encoder.encode(raw);

        ApiKey entity = ApiKey.builder()
                .keyPrefix(prefix)
                .keyHash(hash)
                .name(name)
                .ownerEmail(ownerEmail)
                .rateLimitRpm(rateLimitRpm)
                .active(true)
                .createdAt(Instant.now())
                .build();

        return new IssuedKey(raw, repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.length() < PREFIX_INDEX_LEN) {
            return Optional.empty();
        }
        String prefix = rawKey.substring(0, PREFIX_INDEX_LEN);
        return repository.findByKeyPrefixAndActiveTrue(prefix)
                .filter(k -> encoder.matches(rawKey, k.getKeyHash()));
    }
}
