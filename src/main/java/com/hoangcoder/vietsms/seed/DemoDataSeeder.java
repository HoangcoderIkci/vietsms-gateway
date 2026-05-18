package com.hoangcoder.vietsms.seed;

import com.hoangcoder.vietsms.security.ApiKeyRepository;
import com.hoangcoder.vietsms.security.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;

    @Override
    public void run(String... args) {
        if (apiKeyRepository.countByActiveTrue() > 0) {
            log.info("API keys already exist, skipping seed.");
            return;
        }
        ApiKeyService.IssuedKey issued = apiKeyService.issue(
                "demo", "demo@example.com", 10);
        log.info("");
        log.info("=========================================================");
        log.info("  VietSMS demo API key (save this — shown only once)");
        log.info("  {}", issued.rawKey());
        log.info("=========================================================");
        log.info("");
    }
}
