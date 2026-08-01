package com.builtbygrain.backend.customer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAuthThrottleService {

    public enum Action {
        REGISTER,
        PASSWORD_RESET_REQUEST,
        PASSWORD_RESET
    }

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int LIMIT = 5;

    private final JdbcTemplate jdbc;

    public CustomerAuthThrottleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean consume(Action action, String remoteAddress) {
        Instant now = Instant.now();
        String scopeHash = hash(remoteAddress == null ? "" : remoteAddress.trim());
        Instant since = now.minus(WINDOW);
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_auth_requests WHERE action=? AND scope_hash=? AND attempted_at>=?",
            Long.class,
            action.name(),
            scopeHash,
            Timestamp.from(since)
        );
        if (count != null && count >= LIMIT) {
            return false;
        }
        jdbc.update(
            "INSERT INTO customer_auth_requests(action,scope_hash,attempted_at) VALUES (?,?,?)",
            action.name(),
            scopeHash,
            Timestamp.from(now)
        );
        jdbc.update(
            "DELETE FROM customer_auth_requests WHERE attempted_at < ?",
            Timestamp.from(now.minus(Duration.ofDays(1)))
        );
        return true;
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
