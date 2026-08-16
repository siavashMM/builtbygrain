package com.builtbygrain.backend.customer;

import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetStore {

    private final JdbcTemplate jdbc;
    private final String customerPasswordLockSql;
    private final String tokenClaimSql;

    public PasswordResetStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        boolean postgreSql = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
            "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())
        ));
        this.customerPasswordLockSql = """
            SELECT id,normalized_email,password_hash,version
            FROM customers
            WHERE id=?
            """ + (postgreSql ? "FOR NO KEY UPDATE" : "FOR UPDATE");
        this.tokenClaimSql = postgreSql
            ? """
                WITH claim_time AS MATERIALIZED (
                    SELECT clock_timestamp() AS claimed_at
                )
                UPDATE customer_password_reset_tokens AS token
                SET used_at=claim_time.claimed_at
                FROM claim_time
                WHERE token.token_hash=?
                  AND token.used_at IS NULL
                  AND token.expires_at>claim_time.claimed_at
                """
            : """
                UPDATE customer_password_reset_tokens
                SET used_at=CURRENT_TIMESTAMP
                WHERE token_hash=?
                  AND used_at IS NULL
                  AND expires_at>CURRENT_TIMESTAMP
                """;
    }

    public Optional<Long> findTokenCustomerId(String tokenHash) {
        return jdbc.query(
            "SELECT customer_id FROM customer_password_reset_tokens WHERE token_hash=?",
            (result, rowNumber) -> result.getLong("customer_id"),
            tokenHash
        ).stream().findFirst();
    }

    public boolean lockTokenForClaim(String tokenHash) {
        return !jdbc.query(
            """
            SELECT token_hash
            FROM customer_password_reset_tokens
            WHERE token_hash=?
            FOR UPDATE
            """,
            (result, rowNumber) -> result.getString("token_hash"),
            tokenHash
        ).isEmpty();
    }

    public boolean claimUsableToken(String tokenHash) {
        return jdbc.update(tokenClaimSql, tokenHash) == 1;
    }

    public Optional<CustomerPasswordState> lockCustomerPassword(Long customerId) {
        return jdbc.query(
            customerPasswordLockSql,
            (result, rowNumber) -> new CustomerPasswordState(
                result.getLong("id"),
                result.getString("normalized_email"),
                result.getString("password_hash"),
                result.getLong("version")
            ),
            customerId
        ).stream().findFirst();
    }

    public boolean updatePasswordIfCurrent(
        Long customerId,
        String currentHash,
        long expectedVersion,
        String replacementHash
    ) {
        return jdbc.update(
            """
            UPDATE customers
            SET password_hash=?,updated_at=CURRENT_TIMESTAMP,version=version + 1
            WHERE id=?
              AND version=?
              AND (password_hash=? OR (password_hash IS NULL AND ? IS NULL))
            """,
            replacementHash,
            customerId,
            expectedVersion,
            currentHash,
            currentHash
        ) == 1;
    }

    public void invalidateUnusedTokens(Long customerId) {
        jdbc.update(
            """
            UPDATE customer_password_reset_tokens
            SET used_at=CURRENT_TIMESTAMP
            WHERE customer_id=? AND used_at IS NULL
            """,
            customerId
        );
    }

    public record CustomerPasswordState(Long id, String normalizedEmail, String passwordHash, long version) {
    }
}
