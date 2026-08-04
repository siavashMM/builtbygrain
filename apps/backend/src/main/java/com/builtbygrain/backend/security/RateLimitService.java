package com.builtbygrain.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitService {

    private static final Duration RETENTION_GRACE = Duration.ofHours(1);
    private final JdbcTemplate jdbc;
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    public RateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Decision consume(List<Rule> rules) {
        Instant now = databaseTime();
        List<Target> targets = rules.stream()
            .map(rule -> new Target(rule, hash(rule.subject()), bucket(rule, now)))
            .sorted(Comparator.comparing((Target target) -> target.rule().policy())
                .thenComparing(Target::scopeHash)
                .thenComparingLong(target -> target.bucket().startedAt()))
            .toList();

        for (Target target : targets) {
            ensureBucket(target);
        }

        boolean allowed = true;
        long retryAfterSeconds = 0;

        for (Target target : targets) {
            Integer count = jdbc.queryForObject(
                """
                SELECT request_count
                FROM rate_limit_buckets
                WHERE policy=? AND scope_hash=? AND window_started_epoch_seconds=?
                FOR UPDATE
                """,
                Integer.class,
                target.rule().policy(), target.scopeHash(), target.bucket().startedAt()
            );
            if (count != null && count >= target.rule().limit().getAttempts()) {
                allowed = false;
                retryAfterSeconds = Math.max(
                    retryAfterSeconds,
                    target.bucket().retryAfterSeconds(now)
                );
            }
        }

        if (allowed) {
            for (Target target : targets) {
                jdbc.update(
                    """
                    UPDATE rate_limit_buckets
                    SET request_count=request_count+1
                    WHERE policy=? AND scope_hash=? AND window_started_epoch_seconds=?
                    """,
                    target.rule().policy(), target.scopeHash(), target.bucket().startedAt()
                );
            }
        }

        maybeCleanup(now);
        return new Decision(allowed, retryAfterSeconds);
    }

    @Transactional(readOnly = true)
    public Decision check(List<Rule> rules) {
        Instant now = databaseTime();
        boolean allowed = true;
        long retryAfterSeconds = 0;

        for (Rule rule : rules) {
            Bucket bucket = bucket(rule, now);
            Integer count = jdbc.queryForObject(
                """
                SELECT COALESCE(MAX(request_count), 0)
                FROM rate_limit_buckets
                WHERE policy=? AND scope_hash=? AND window_started_epoch_seconds=?
                """,
                Integer.class,
                rule.policy(), hash(rule.subject()), bucket.startedAt()
            );
            if (count != null && count >= rule.limit().getAttempts()) {
                allowed = false;
                retryAfterSeconds = Math.max(retryAfterSeconds, bucket.retryAfterSeconds(now));
            }
        }
        return new Decision(allowed, retryAfterSeconds);
    }

    @Transactional
    public void reset(String policy, String subject) {
        jdbc.update(
            "DELETE FROM rate_limit_buckets WHERE policy=? AND scope_hash=?",
            policy,
            hash(subject)
        );
    }

    private void ensureBucket(Target target) {
        jdbc.update(
            """
            INSERT INTO rate_limit_buckets(
                policy,scope_hash,window_started_epoch_seconds,request_count,expires_at_epoch_seconds
            ) VALUES(?,?,?,0,?)
            ON CONFLICT DO NOTHING
            """,
            target.rule().policy(),
            target.scopeHash(),
            target.bucket().startedAt(),
            target.bucket().expiresAt()
        );
    }

    private Bucket bucket(Rule rule, Instant now) {
        long windowSeconds = rule.limit().getWindow().toSeconds();
        if (windowSeconds < 1) {
            throw new IllegalStateException("Rate-limit windows must be at least one second");
        }
        long startedAt = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        return new Bucket(startedAt, startedAt + windowSeconds);
    }

    private void maybeCleanup(Instant now) {
        if (cleanupCounter.incrementAndGet() % 100 == 0) {
            jdbc.update(
                "DELETE FROM rate_limit_buckets WHERE expires_at_epoch_seconds<?",
                now.minus(RETENTION_GRACE).getEpochSecond()
            );
        }
    }

    private Instant databaseTime() {
        Timestamp timestamp = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        if (timestamp == null) throw new IllegalStateException("Database time is unavailable");
        return timestamp.toInstant();
    }

    private String hash(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Rule(String policy, String subject, RateLimitProperties.Limit limit) {
        public Rule {
            if (policy == null || policy.isBlank()) throw new IllegalArgumentException("A policy is required");
            if (limit == null) throw new IllegalArgumentException("A limit is required");
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static Decision permit() { return new Decision(true, 0); }
    }

    private record Target(Rule rule, String scopeHash, Bucket bucket) {
    }

    private record Bucket(long startedAt, long expiresAt) {
        long retryAfterSeconds(Instant now) {
            return Math.max(1, expiresAt - now.getEpochSecond());
        }
    }
}
