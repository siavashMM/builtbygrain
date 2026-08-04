package com.builtbygrain.backend.customer;

import static com.builtbygrain.backend.security.RateLimitService.Rule;

import java.util.List;
import java.util.Locale;

import com.builtbygrain.backend.security.RateLimitProperties;
import com.builtbygrain.backend.security.RateLimitService;
import com.builtbygrain.backend.security.ClientAddress;
import org.springframework.stereotype.Service;

@Service
public class CustomerAuthThrottleService {

    private static final String REGISTRATION_IP_POLICY = "CUSTOMER_REGISTRATION_IP";
    private static final String RESET_REQUEST_ACCOUNT_POLICY = "PASSWORD_RESET_REQUEST_ACCOUNT";
    private static final String RESET_REQUEST_IP_POLICY = "PASSWORD_RESET_REQUEST_IP";
    private static final String RESET_TOKEN_POLICY = "PASSWORD_RESET_TOKEN";
    private static final String RESET_IP_POLICY = "PASSWORD_RESET_IP";

    private final RateLimitService rateLimits;
    private final RateLimitProperties properties;

    public CustomerAuthThrottleService(RateLimitService rateLimits, RateLimitProperties properties) {
        this.rateLimits = rateLimits;
        this.properties = properties;
    }

    public RateLimitService.Decision consumeRegistration(String remoteAddress) {
        return rateLimits.consume(List.of(new Rule(
            REGISTRATION_IP_POLICY,
            normalizeAddress(remoteAddress),
            properties.getRegistrationIp()
        )));
    }

    public RateLimitService.Decision consumePasswordResetRequest(String email, String remoteAddress) {
        return rateLimits.consume(List.of(
            new Rule(
                RESET_REQUEST_ACCOUNT_POLICY,
                normalizeEmail(email),
                properties.getPasswordResetRequestAccount()
            ),
            new Rule(
                RESET_REQUEST_IP_POLICY,
                normalizeAddress(remoteAddress),
                properties.getPasswordResetRequestIp()
            )
        ));
    }

    public RateLimitService.Decision consumePasswordReset(String token, String remoteAddress) {
        return rateLimits.consume(List.of(
            new Rule(RESET_TOKEN_POLICY, token, properties.getPasswordResetToken()),
            new Rule(RESET_IP_POLICY, normalizeAddress(remoteAddress), properties.getPasswordResetIp())
        ));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAddress(String remoteAddress) {
        return ClientAddress.normalize(remoteAddress);
    }
}
