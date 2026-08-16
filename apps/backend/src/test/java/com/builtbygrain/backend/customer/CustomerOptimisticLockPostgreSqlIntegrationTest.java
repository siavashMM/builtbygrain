package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CustomerOptimisticLockPostgreSqlIntegrationTest {

    private static final String EMAIL = "stale-customer@example.test";
    private static final String CURRENT_PASSWORD = "current stale customer password";
    private static final String RESET_PASSWORD = "replacement stale customer password";
    private static final String GENERIC_INVALID_TOKEN = "This password reset link is invalid or has expired";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;
    @Autowired CustomerSocialIdentityRepository identities;
    @Autowired CustomerPasswordService passwords;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearCustomerData() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM customer_password_reset_tokens");
        jdbc.update("DELETE FROM customer_addresses");
        jdbc.update("DELETE FROM customer_social_identities");
        jdbc.update("DELETE FROM customers");
    }

    @Test
    void staleProfileFlushFailsWithoutRestoringPasswordAfterReset() throws Exception {
        Long customerId = createCustomer();
        String rawToken = "profile-collision-reset-token";
        insertUsableToken(customerId, rawToken);
        insertSession();
        insertSession();

        Throwable staleFailure = collideWithReset(
            rawToken,
            customer -> customer.updateProfile(
                "changed@example.test",
                "changed@example.test",
                "Changed",
                "Profile",
                "+49 123",
                "de"
            ),
            customer -> customers.flush()
        );

        assertOptimisticConflict(staleFailure);
        assertResetWon(customerId, rawToken);
        assertThat(jdbc.queryForObject(
            "SELECT normalized_email FROM customers WHERE id=?",
            String.class,
            customerId
        )).isEqualTo(EMAIL);
    }

    @Test
    void staleSocialIdentityFlushFailsWithoutRestoringPasswordAfterReset() throws Exception {
        Long customerId = createCustomer();
        String rawToken = "social-collision-reset-token";
        insertUsableToken(customerId, rawToken);
        insertSession();

        Throwable staleFailure = collideWithReset(
            rawToken,
            Customer::markEmailVerified,
            customer -> identities.saveAndFlush(new CustomerSocialIdentity(
                customer,
                "google",
                "stale-social-subject"
            ))
        );

        assertOptimisticConflict(staleFailure);
        assertResetWon(customerId, rawToken);
        assertThat(jdbc.queryForObject(
            "SELECT email_verified_at IS NULL FROM customers WHERE id=?",
            Boolean.class,
            customerId
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_social_identities WHERE customer_id=?",
            Integer.class,
            customerId
        )).isZero();
    }

    private Throwable collideWithReset(
        String rawToken,
        Consumer<Customer> staleMutation,
        Consumer<Customer> staleFlush
    ) throws Exception {
        CountDownLatch staleCustomerLoaded = new CountDownLatch(1);
        CountDownLatch resumeStaleTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Throwable> staleResult = executor.submit(() -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Customer staleCustomer = customers.findByNormalizedEmail(EMAIL).orElseThrow();
                    staleMutation.accept(staleCustomer);
                    staleCustomerLoaded.countDown();
                    await(resumeStaleTransaction, "Timed out waiting to resume the stale customer transaction");
                    staleFlush.accept(staleCustomer);
                });
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        });

        try {
            assertThat(staleCustomerLoaded.await(10, TimeUnit.SECONDS))
                .as("the stale transaction loaded and changed the customer before reset")
                .isTrue();
            passwords.resetPassword(rawToken, RESET_PASSWORD);
            resumeStaleTransaction.countDown();
            return staleResult.get(10, TimeUnit.SECONDS);
        } finally {
            resumeStaleTransaction.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertResetWon(Long customerId, String rawToken) {
        assertThat(authenticates(RESET_PASSWORD)).isTrue();
        assertThat(authenticates(CURRENT_PASSWORD)).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT version FROM customers WHERE id=?",
            Long.class,
            customerId
        )).isOne();
        assertThat(jdbc.queryForObject(
            "SELECT used_at IS NOT NULL FROM customer_password_reset_tokens WHERE token_hash=?",
            Boolean.class,
            hash(rawToken)
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isZero();
        assertThatThrownBy(() -> passwords.resetPassword(rawToken, "another replacement password"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo(GENERIC_INVALID_TOKEN);
            });
    }

    private void assertOptimisticConflict(Throwable failure) {
        assertThat(failure)
            .as("the stale Hibernate flush must fail its version predicate")
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private Long createCustomer() {
        jdbc.update(
            """
            INSERT INTO customers(
                email,normalized_email,password_hash,first_name,last_name,locale,
                account_status,created_at,updated_at
            ) VALUES(?,?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,
            EMAIL,
            EMAIL,
            passwordEncoder.encode(CURRENT_PASSWORD),
            "Stale",
            "Customer",
            "en"
        );
        return jdbc.queryForObject(
            "SELECT id FROM customers WHERE normalized_email=?",
            Long.class,
            EMAIL
        );
    }

    private void insertUsableToken(Long customerId, String rawToken) {
        jdbc.update(
            """
            INSERT INTO customer_password_reset_tokens(
                customer_id,token_hash,expires_at,used_at,created_at
            ) VALUES(?,?,CURRENT_TIMESTAMP + INTERVAL '30 minutes',NULL,CURRENT_TIMESTAMP)
            """,
            customerId,
            hash(rawToken)
        );
    }

    private void insertSession() {
        String primaryId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update(
            """
            INSERT INTO spring_session(
                primary_id,session_id,creation_time,last_access_time,
                max_inactive_interval,expiry_time,principal_name
            ) VALUES(?,?,?,?,?,?,?)
            """,
            primaryId,
            UUID.randomUUID().toString(),
            now,
            now,
            1800,
            now + TimeUnit.MINUTES.toMillis(30),
            EMAIL
        );
    }

    private boolean authenticates(String rawPassword) {
        String storedHash = jdbc.queryForObject(
            "SELECT password_hash FROM customers WHERE normalized_email=?",
            String.class,
            EMAIL
        );
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(username ->
            User.withUsername(EMAIL).password(storedHash).roles("CUSTOMER").build()
        );
        provider.setPasswordEncoder(passwordEncoder);
        try {
            provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, rawPassword));
            return true;
        } catch (AuthenticationException exception) {
            return false;
        }
    }

    private static void await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Stale transaction synchronization was interrupted", exception);
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
