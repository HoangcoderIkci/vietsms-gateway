package com.hoangcoder.vietsms.common;

import com.hoangcoder.vietsms.security.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@Tag(name = "Health", description = "Authenticated liveness check")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Authenticated ping — confirms API key is valid")
    public Map<String, Object> ping(@AuthenticationPrincipal ApiKey key) {
        return Map.of(
                "ok", true,
                "key_name", key.getName(),
                "key_prefix", key.getKeyPrefix(),
                "server_time", Instant.now().toString()
        );
    }
}
