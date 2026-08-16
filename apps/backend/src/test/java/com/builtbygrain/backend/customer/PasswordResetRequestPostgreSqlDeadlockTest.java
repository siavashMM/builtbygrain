package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(PasswordResetRequestPostgreSqlDeadlockTest.InterlockConfiguration.class)
class PasswordResetRequestPostgreSqlDeadlockTest {

    private static final String EMAIL = "reset-ordering@example.test";
    private static final String CURRENT_PASSWORD = "current reset ordering password";
    private static final String RESET_PASSWORD = "replacement reset ordering password";
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
    @Autowired CustomerPasswordService passwords;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ResetRequestInterlock resetRequestInterlock;

    @MockitoBean PasswordResetMailService mail;
    @MockitoSpyBean PasswordResetStore resetStore;

    @BeforeEach
    void clearCustomerData() {
        resetRequestInterlock.disable();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM customer_password_reset_tokens");
        jdbc.update("DELETE FROM customer_addresses");
        jdbc.update("DELETE FROM customer_social_identities");
        jdbc.update("DELETE FROM customers");
    }

    @Test
    void resetRequestTokenOperationFirstDoesNotDeadlockReset() throws Exception {
        Long customerId = createCustomer();
        String rawToken = "request-first-original-reset-token";
        insertUsableToken(customerId, rawToken);
        insertSession();

        CountDownLatch requestTokenLocked = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        CountDownLatch customerLocked = new CountDownLatch(1);
        CountDownLatch tokenLockEntered = new CountDownLatch(1);

        resetRequestInterlock.pauseAfterTokenOperation(requestTokenLocked, releaseRequest);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            customerLocked.countDown();
            return result;
        }).when(resetStore).lockCustomerPassword(anyLong());
        doAnswer(invocation -> {
            tokenLockEntered.countDown();
            return invocation.callRealMethod();
        }).when(resetStore).lockTokenForClaim(anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Throwable> request = executor.submit(this::requestResetOutcome);
        Future<Throwable> reset = null;
        try {
            assertThat(requestTokenLocked.await(10, TimeUnit.SECONDS))
                .as("the reset request updated and locked the original token")
                .isTrue();
            reset = executor.submit(() -> resetOutcome(rawToken));
            assertThat(customerLocked.await(10, TimeUnit.SECONDS))
                .as("the reset transaction acquired the customer NO KEY UPDATE lock")
                .isTrue();
            assertThat(tokenLockEntered.await(10, TimeUnit.SECONDS))
                .as("the reset transaction attempted to lock the token before claiming it")
                .isTrue();
            awaitBlockedTokenMutation();
            releaseRequest.countDown();

            assertThat(request.get(10, TimeUnit.SECONDS)).isNull();
            assertGenericInvalidToken(reset.get(10, TimeUnit.SECONDS));
        } finally {
            releaseRequest.countDown();
            cancel(reset);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(authenticates(CURRENT_PASSWORD)).isTrue();
        assertThat(authenticates(RESET_PASSWORD)).isFalse();
        assertTokenAndSessionState(customerId, rawToken, 0L, 1);
        assertGenericInvalidToken(resetOutcome(rawToken));
        verify(mail, times(1)).send(any(Customer.class), anyString());
    }

    @Test
    void resetClaimFirstDoesNotDeadlockResetRequest() throws Exception {
        Long customerId = createCustomer();
        String rawToken = "reset-first-original-reset-token";
        insertUsableToken(customerId, rawToken);
        insertSession();

        CountDownLatch tokenClaimed = new CountDownLatch(1);
        CountDownLatch releaseReset = new CountDownLatch(1);
        CountDownLatch requestTokenOperationEntered = new CountDownLatch(1);

        resetRequestInterlock.signalBeforeTokenOperation(requestTokenOperationEntered);
        doAnswer(invocation -> {
            boolean claimed = (boolean) invocation.callRealMethod();
            if (claimed) {
                tokenClaimed.countDown();
                await(releaseReset, "Timed out waiting to release the password reset");
            }
            return claimed;
        }).when(resetStore).claimUsableToken(anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Throwable> reset = executor.submit(() -> resetOutcome(rawToken));
        Future<Throwable> request = null;
        try {
            assertThat(tokenClaimed.await(10, TimeUnit.SECONDS))
                .as("the reset transaction claimed the token while holding the customer lock")
                .isTrue();
            request = executor.submit(this::requestResetOutcome);
            assertThat(requestTokenOperationEntered.await(10, TimeUnit.SECONDS))
                .as("the reset request entered its token invalidation operation")
                .isTrue();
            awaitBlockedTokenMutation();
            releaseReset.countDown();

            assertThat(reset.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(request.get(10, TimeUnit.SECONDS)).isNull();
        } finally {
            releaseReset.countDown();
            cancel(request);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(authenticates(RESET_PASSWORD)).isTrue();
        assertThat(authenticates(CURRENT_PASSWORD)).isFalse();
        assertTokenAndSessionState(customerId, rawToken, 1L, 0);
        assertGenericInvalidToken(resetOutcome(rawToken));
        verify(mail, times(1)).send(any(Customer.class), anyString());
    }

    private Throwable requestResetOutcome() {
        try {
            passwords.requestReset(EMAIL);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private Throwable resetOutcome(String rawToken) {
        try {
            passwords.resetPassword(rawToken, RESET_PASSWORD);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void awaitBlockedTokenMutation() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_stat_activity
                WHERE datname=current_database()
                  AND cardinality(pg_blocking_pids(pid))>0
                  AND query ILIKE '%customer_password_reset_tokens%'
                """,
                Integer.class
            );
            if (blocked != null && blocked > 0) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("PostgreSQL did not report the expected blocked token mutation");
    }

    private void assertTokenAndSessionState(
        Long customerId,
        String rawToken,
        long expectedVersion,
        int expectedSessions
    ) {
        assertThat(jdbc.queryForObject(
            "SELECT used_at IS NOT NULL FROM customer_password_reset_tokens WHERE token_hash=?",
            Boolean.class,
            hash(rawToken)
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_password_reset_tokens WHERE customer_id=?",
            Integer.class,
            customerId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_password_reset_tokens WHERE customer_id=? AND used_at IS NULL",
            Integer.class,
            customerId
        )).isOne();
        assertThat(jdbc.queryForObject(
            "SELECT version FROM customers WHERE id=?",
            Long.class,
            customerId
        )).isEqualTo(expectedVersion);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isEqualTo(expectedSessions);
    }

    private void assertGenericInvalidToken(Throwable failure) {
        assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getReason()).isEqualTo(GENERIC_INVALID_TOKEN);
        });
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
            "Reset",
            "Ordering",
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

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Password-reset synchronization was interrupted", exception);
        }
    }

    private static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class InterlockConfiguration {

        @Bean
        ResetRequestInterlock resetRequestInterlock() {
            return new ResetRequestInterlock();
        }
    }

    @Aspect
    static class ResetRequestInterlock {

        private volatile CountDownLatch beforeSignal;
        private volatile CountDownLatch afterSignal;
        private volatile CountDownLatch afterRelease;

        void signalBeforeTokenOperation(CountDownLatch signal) {
            beforeSignal = signal;
        }

        void pauseAfterTokenOperation(CountDownLatch signal, CountDownLatch release) {
            afterSignal = signal;
            afterRelease = release;
        }

        void disable() {
            beforeSignal = null;
            afterSignal = null;
            afterRelease = null;
        }

        @Around("execution(* com.builtbygrain.backend.customer.PasswordResetTokenRepository.invalidateUnusedForCustomer(..))")
        Object synchronizeTokenOperation(ProceedingJoinPoint invocation) throws Throwable {
            CountDownLatch signalBefore = beforeSignal;
            if (signalBefore != null) {
                signalBefore.countDown();
            }
            Object result = invocation.proceed();
            CountDownLatch signalAfter = afterSignal;
            CountDownLatch releaseAfter = afterRelease;
            if (signalAfter != null && releaseAfter != null) {
                signalAfter.countDown();
                await(releaseAfter, "Timed out waiting to release the reset request");
            }
            return result;
        }
    }
}
