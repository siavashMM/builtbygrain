package com.builtbygrain.backend.security;

import static com.builtbygrain.backend.security.RateLimitService.Rule;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class LoginThrottleService {

    private static final String CUSTOMER_ACCOUNT_POLICY = "CUSTOMER_LOGIN_ACCOUNT";
    private static final String CUSTOMER_IP_POLICY = "CUSTOMER_LOGIN_IP";
    private static final String ADMIN_ACCOUNT_POLICY = "ADMIN_LOGIN_ACCOUNT";
    private static final String ADMIN_IP_POLICY = "ADMIN_LOGIN_IP";

    private final RateLimitService rateLimits;
    private final RateLimitProperties properties;

    public LoginThrottleService(RateLimitService rateLimits, RateLimitProperties properties) {
        this.rateLimits = rateLimits;
        this.properties = properties;
    }

    public RateLimitService.Decision reserveAttempt(
        Audience audience,
        String username,
        String remoteAddress
    ) {
        return rateLimits.consume(rules(audience, username, remoteAddress));
    }

    public void recordSuccess(Audience audience, String username) {
        rateLimits.reset(accountPolicy(audience), normalizeAccount(username));
    }

    private List<Rule> rules(Audience audience, String username, String remoteAddress) {
        if (audience == Audience.ADMIN) {
            return List.of(
                new Rule(ADMIN_ACCOUNT_POLICY, normalizeAccount(username), properties.getAdminLoginAccount()),
                new Rule(ADMIN_IP_POLICY, normalizeAddress(remoteAddress), properties.getAdminLoginIp())
            );
        }
        return List.of(
            new Rule(CUSTOMER_ACCOUNT_POLICY, normalizeAccount(username), properties.getCustomerLoginAccount()),
            new Rule(CUSTOMER_IP_POLICY, normalizeAddress(remoteAddress), properties.getCustomerLoginIp())
        );
    }

    private String accountPolicy(Audience audience) {
        return audience == Audience.ADMIN ? ADMIN_ACCOUNT_POLICY : CUSTOMER_ACCOUNT_POLICY;
    }

    private String normalizeAccount(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAddress(String remoteAddress) {
        return ClientAddress.normalize(remoteAddress);
    }

    public enum Audience {
        ADMIN,
        CUSTOMER
    }
}
