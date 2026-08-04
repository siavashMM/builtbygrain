package com.builtbygrain.backend.customer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerPasswordService {

    private final CustomerRepository customers;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailService mail;
    private final CustomerSessionService sessions;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration resetLifetime;

    public CustomerPasswordService(
        CustomerRepository customers,
        PasswordResetTokenRepository resetTokens,
        PasswordEncoder passwordEncoder,
        PasswordResetMailService mail,
        CustomerSessionService sessions,
        @Value("${app.customer-auth.password-reset-lifetime:30m}") Duration resetLifetime
    ) {
        this.customers = customers;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.sessions = sessions;
        this.resetLifetime = resetLifetime;
    }

    @Transactional
    public void requestReset(String email) {
        customers.findByNormalizedEmail(CustomerAccountService.normalizeEmail(email))
            .filter(Customer::isActive)
            .ifPresent(customer -> {
                LocalDateTime now = LocalDateTime.now();
                resetTokens.invalidateUnusedForCustomer(customer.getId(), now);
                String rawToken = generateToken();
                resetTokens.save(new PasswordResetToken(
                    customer,
                    hash(rawToken),
                    now.plus(resetLifetime)
                ));
                mail.send(customer, rawToken);
            });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokens.findByTokenHash(hash(rawToken))
            .orElseThrow(CustomerPasswordService::invalidResetToken);
        LocalDateTime now = LocalDateTime.now();
        if (!token.canUseAt(now)) {
            throw invalidResetToken();
        }
        Customer customer = token.getCustomer();
        if (customer.getPasswordHash() != null && passwordEncoder.matches(newPassword, customer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "New password must be different from the current password");
        }
        customer.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        resetTokens.invalidateUnusedForCustomer(customer.getId(), now);
        sessions.invalidateAll(customer.getNormalizedEmail());
    }

    @Transactional
    public void changePassword(Customer customer, String currentPassword, String newPassword) {
        if (customer.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No password is set for this account. Request a password setup link instead");
        }
        if (!passwordEncoder.matches(currentPassword, customer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, customer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "New password must be different from the current password");
        }
        customer.changePasswordHash(passwordEncoder.encode(newPassword));
        customers.save(customer);
        resetTokens.invalidateUnusedForCustomer(customer.getId(), LocalDateTime.now());
        sessions.invalidateAll(customer.getNormalizedEmail());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException invalidResetToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "This password reset link is invalid or has expired");
    }
}
