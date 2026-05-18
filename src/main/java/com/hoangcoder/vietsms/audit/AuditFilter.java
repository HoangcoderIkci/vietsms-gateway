package com.hoangcoder.vietsms.audit;

import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.security.ApiKeyPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";

    private final AuditService auditService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                ApiKey key = currentApiKey();
                auditService.record(
                        key != null ? key.getId() : null,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        null,
                        requestId);
            } finally {
                MDC.remove(MDC_REQUEST_ID);
            }
        }
    }

    private ApiKey currentApiKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyPrincipal p) return p.getApiKey();
        return null;
    }
}
