package com.hoangcoder.vietsms.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class ApiKeyPrincipal extends AbstractAuthenticationToken {

    private final ApiKey apiKey;

    public ApiKeyPrincipal(ApiKey apiKey) {
        super(Collections.singletonList(() -> "ROLE_CLIENT"));
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return apiKey.getKeyHash();
    }

    @Override
    public Object getPrincipal() {
        return apiKey;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public Long getApiKeyId() {
        return apiKey.getId();
    }
}
