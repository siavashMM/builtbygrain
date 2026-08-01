package com.builtbygrain.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.builtbygrain.backend.admin.AdminAccountRepository;
import com.builtbygrain.backend.customer.CustomerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginThrottleService {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration RETENTION = Duration.ofHours(24);
    static final int ACCOUNT_IP_LIMIT = 5;
    static final int IP_LIMIT = 20;

    private final JdbcTemplate jdbc;
    private final AdminAccountRepository adminAccounts;
    private final CustomerRepository customers;
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    public LoginThrottleService(
        JdbcTemplate jdbc,
        AdminAccountRepository adminAccounts,
        CustomerRepository customers
    ) {
        this.jdbc = jdbc;
        this.adminAccounts = adminAccounts;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String username, String remoteAddress) {
        Instant since = Instant.now().minus(WINDOW);
        if (count("IP", hash(normalizeAddress(remoteAddress)), since) >= IP_LIMIT) {
            return true;
        }
        return isKnownAccount(username)
            && count("ACCOUNT_IP", accountIpHash(username, remoteAddress), since) >= ACCOUNT_IP_LIMIT;
    }

    @Transactional
    public boolean recordFailure(String username, String remoteAddress) {
        maybeCleanup();
        Instant now = Instant.now();
        insert("IP", hash(normalizeAddress(remoteAddress)), now);
        if (isKnownAccount(username)) {
            insert("ACCOUNT_IP", accountIpHash(username, remoteAddress), now);
        }
        return isBlocked(username, remoteAddress);
    }

    @Transactional
    public void recordSuccess(String username, String remoteAddress) {
        jdbc.update(
            "DELETE FROM admin_login_attempts WHERE (scope_type=? AND scope_hash=?) OR (scope_type=? AND scope_hash=?)",
            "IP", hash(normalizeAddress(remoteAddress)),
            "ACCOUNT_IP", accountIpHash(username, remoteAddress)
        );
    }

    private boolean isKnownAccount(String username) {
        if (username == null) {
            return false;
        }
        String normalized = username.trim();
        return adminAccounts.findByUsernameIgnoreCase(normalized).isPresent()
            || customers.existsByNormalizedEmail(normalized.toLowerCase(Locale.ROOT));
    }

    private long count(String scopeType, String scopeHash, Instant since) {
        Long result = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_login_attempts WHERE scope_type=? AND scope_hash=? AND failed_at>=?",
            Long.class,
            scopeType,
            scopeHash,
            Timestamp.from(since)
        );
        return result == null ? 0 : result;
    }

    private void insert(String scopeType, String scopeHash, Instant now) {
        jdbc.update(
            "INSERT INTO admin_login_attempts(scope_type,scope_hash,failed_at) VALUES(?,?,?)",
            scopeType,
            scopeHash,
            Timestamp.from(now)
        );
    }

    private void maybeCleanup() {
        if (cleanupCounter.incrementAndGet() % 100 == 0) {
            jdbc.update(
                "DELETE FROM admin_login_attempts WHERE failed_at<?",
                Timestamp.from(Instant.now().minus(RETENTION))
            );
        }
    }

    private String accountIpHash(String username, String remoteAddress) {
        String account = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return hash(account + "\u0000" + normalizeAddress(remoteAddress));
    }

    private String normalizeAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
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
}
