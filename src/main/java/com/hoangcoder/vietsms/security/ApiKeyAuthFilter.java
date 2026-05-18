package com.hoangcoder.vietsms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangcoder.vietsms.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "x-api-key";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            unauthorized(response, "MISSING_API_KEY", "Header x-api-key is required");
            return;
        }

        Optional<ApiKey> match = apiKeyService.authenticate(raw);
        if (match.isEmpty()) {
            unauthorized(response, "INVALID_API_KEY", "API key is invalid or revoked");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new ApiKeyPrincipal(match.get()));
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(401)
                .error(code)
                .message(message)
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
