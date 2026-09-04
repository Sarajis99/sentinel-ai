package com.sentinel.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ApiKeyAuthFilter — enforces API Key authorization on public REST endpoints.
 *
 * Prevents unauthorized external access, port scanners, and unauthenticated
 * bots from invoking destructive actions or flooding backend services.
 */
@Slf4j
@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Sentinel-Api-Key";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${security.api-key:sentinel-default-api-key}")
    private String configuredApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 0. If security is disabled (e.g. in integration test profile), pass through
        if (!securityEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Always permit CORS preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 2. Permit health, ping, and warm-up monitoring endpoints without authentication
        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Only apply auth to /api/v1/** endpoints
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Extract API Key from X-Sentinel-Api-Key or Authorization Bearer header
        String providedKey = extractApiKey(request);

        if (providedKey != null && isValidKey(providedKey)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("⛔ Unauthorized request blocked: method={} path={} remoteIp={}",
                    request.getMethod(), path, request.getRemoteAddr());
            sendUnauthorizedResponse(response);
        }
    }

    private boolean isExemptPath(String path) {
        return path.equals("/ping")
                || path.equals("/health")
                || path.startsWith("/api/v1/health");
    }

    private String extractApiKey(HttpServletRequest request) {
        String key = request.getHeader(API_KEY_HEADER);
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        return null;
    }

    private boolean isValidKey(String providedKey) {
        if (configuredApiKey == null || configuredApiKey.trim().isEmpty()) {
            return true;
        }
        return configuredApiKey.equals(providedKey);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or missing " + API_KEY_HEADER + "\"}"
        );
    }
}
