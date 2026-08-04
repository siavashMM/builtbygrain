package com.builtbygrain.backend.security;

import static com.builtbygrain.backend.security.RateLimitService.Rule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {

    static final String ADMIN_WRITE_POLICY = "ADMIN_WRITE";
    static final String ADMIN_UPLOAD_POLICY = "ADMIN_UPLOAD";
    static final String CUSTOMER_WRITE_POLICY = "CUSTOMER_WRITE";

    private final RateLimitService rateLimits;
    private final RateLimitProperties properties;

    public ApiRateLimitFilter(RateLimitService rateLimits, RateLimitProperties properties) {
        this.rateLimits = rateLimits;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Rule> rules = rules(request, authentication);
        if (!rules.isEmpty()) {
            RateLimitService.Decision decision = rateLimits.consume(rules);
            if (!decision.allowed()) {
                tooManyRequests(response, decision.retryAfterSeconds());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<Rule> rules(HttpServletRequest request, Authentication authentication) {
        if (!isMutation(request) || authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        String path = request.getRequestURI();
        String principal = authentication.getName();
        List<Rule> rules = new ArrayList<>();
        if (path.startsWith("/api/admin/") && hasRole(authentication, "ROLE_ADMIN")) {
            rules.add(new Rule(ADMIN_WRITE_POLICY, principal, properties.getAdminWrite()));
            if (isMultipart(request)) {
                rules.add(new Rule(ADMIN_UPLOAD_POLICY, principal, properties.getAdminUpload()));
            }
        } else if (path.startsWith("/api/account/") && hasRole(authentication, "ROLE_CUSTOMER")) {
            rules.add(new Rule(CUSTOMER_WRITE_POLICY, principal, properties.getCustomerWrite()));
        }
        return rules;
    }

    private boolean isMutation(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT)
            .startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private void tooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        byte[] body = "{\"error\":\"Too many requests. Please try again later.\"}"
            .getBytes(StandardCharsets.UTF_8);
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
