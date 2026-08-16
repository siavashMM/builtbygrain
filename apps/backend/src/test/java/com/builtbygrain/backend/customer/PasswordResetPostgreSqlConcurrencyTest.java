package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PasswordResetPostgreSqlConcurrencyTest {

    private static final String EMAIL = "reset-race@example.test";
    private static final String CURRENT_PASSWORD = "current password for reset race";
    private static final String FIRST_PASSWORD = "first replacement password";
    private static final String SECOND_PASSWORD = "second replacement password";
    private static final String GENERIC_INVALID_TOKEN = "This password reset link is invalid or has expired";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static HikariDataSource firstDataSource;
    static HikariDataSource secondDataSource;
    static JdbcTemplate jdbc;
    static PasswordEncoder passwordEncoder;
    static CustomerPasswordService firstService;
    static CustomerPasswordService secondService;
    static TransactionTemplate firstTransaction;
    static TransactionTemplate secondTransaction;

    @BeforeAll
    static void configureServices() {
        firstDataSource = dataSource();
        secondDataSource = dataSource();
        Flyway.configure().dataSource(firstDataSource).load().migrate();

        jdbc = new JdbcTemplate(firstDataSource);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        firstService = service(firstDataSource, passwordEncoder, sessions(firstDataSource));
        secondService = service(secondDataSource, passwordEncoder, sessions(secondDataSource));
        firstTransaction = new TransactionTemplate(new DataSourceTransactionManager(firstDataSource));
        secondTransaction = new TransactionTemplate(new DataSourceTransactionManager(secondDataSource));
    }

    @AfterAll
    static void closeDataSources() {
        if (firstDataSource != null) firstDataSource.close();
        if (secondDataSource != null) secondDataSource.close();
    }

    @BeforeEach
    void clearSecurityState() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM customer_password_reset_tokens");
        jdbc.update("DELETE FROM customers");
    }

    @Test
    void oneOfTwoConcurrentResetRequestsWinsAndInvalidatesEverySession() throws Exception {
        Long customerId = createCustomer();
        String rawToken = "single-use-reset-token-for-concurrency";
        insertUsableToken(customerId, rawToken);
        insertSession(EMAIL);
        insertSession(EMAIL);

        CoordinatedPasswordEncoder coordinated = new CoordinatedPasswordEncoder(passwordEncoder);
        StoreStageCoordinator storeStage = new StoreStageCoordinator();
        CustomerPasswordService coordinatedFirst = service(
            coordinated,
            sessions(firstDataSource),
            new CoordinatedPasswordResetStore(new JdbcTemplate(firstDataSource), storeStage)
        );
        CustomerPasswordService coordinatedSecond = service(
            coordinated,
            sessions(secondDataSource),
            new CoordinatedPasswordResetStore(new JdbcTemplate(secondDataSource), storeStage)
        );
        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ResetOutcome> first = executor.submit(() -> attemptReset(
            firstTransaction, coordinatedFirst, rawToken, FIRST_PASSWORD, start
        ));
        Future<ResetOutcome> second = executor.submit(() -> attemptReset(
            secondTransaction, coordinatedSecond, rawToken, SECOND_PASSWORD, start
        ));

        List<ResetOutcome> outcomes;
        try {
            start.await(10, TimeUnit.SECONDS);
            assertThat(storeStage.bothRequestsReached.await(10, TimeUnit.SECONDS))
                .as("both transactions reached the token store before either could claim the token")
                .isTrue();
            assertThat(coordinated.firstPasswordCheck.await(10, TimeUnit.SECONDS)).isTrue();
            coordinated.releasePasswordCheck.countDown();
            outcomes = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            coordinated.releasePasswordCheck.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(outcomes).filteredOn(ResetOutcome::succeeded).hasSize(1);
        assertThat(coordinated.passwordChecks).hasValue(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
            .singleElement()
            .satisfies(outcome -> assertThat(outcome.error()).isEqualTo(GENERIC_INVALID_TOKEN));

        String winningPassword = outcomes.stream()
            .filter(ResetOutcome::succeeded)
            .map(ResetOutcome::password)
            .findFirst()
            .orElseThrow();
        String losingPassword = winningPassword.equals(FIRST_PASSWORD) ? SECOND_PASSWORD : FIRST_PASSWORD;
        assertThat(authenticates(winningPassword)).isTrue();
        assertThat(authenticates(losingPassword)).isFalse();
        assertThat(authenticates(CURRENT_PASSWORD)).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isZero();

        assertGenericInvalidToken(() -> firstTransaction.executeWithoutResult(status ->
            firstService.resetPassword(rawToken, "third replacement password")
        ));
    }

    @Test
    void failureAfterClaimRollsBackTokenPasswordAndSessionChanges() {
        Long customerId = createCustomer();
        String rawToken = "rollback-reset-token";
        String siblingToken = "rollback-sibling-reset-token";
        insertUsableToken(customerId, rawToken);
        insertUsableToken(customerId, siblingToken);
        insertSession(EMAIL);

        CustomerSessionService failingSessions = new CustomerSessionService(
            mock(SecurityContextRepository.class),
            new JdbcTemplate(firstDataSource),
            Duration.ofDays(30)
        ) {
            @Override
            public void invalidateAll(String normalizedEmail) {
                super.invalidateAll(normalizedEmail);
                throw new IllegalStateException("session invalidation failed");
            }
        };
        CustomerPasswordService failingService = service(firstDataSource, passwordEncoder, failingSessions);

        assertThatThrownBy(() -> firstTransaction.executeWithoutResult(status ->
            failingService.resetPassword(rawToken, FIRST_PASSWORD)
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("session invalidation failed");

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_password_reset_tokens WHERE customer_id=? AND used_at IS NULL",
            Integer.class,
            customerId
        )).isEqualTo(2);
        assertThat(authenticates(CURRENT_PASSWORD)).isTrue();
        assertThat(authenticates(FIRST_PASSWORD)).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isOne();

        firstTransaction.executeWithoutResult(status -> firstService.resetPassword(rawToken, FIRST_PASSWORD));
        assertThat(authenticates(FIRST_PASSWORD)).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_password_reset_tokens WHERE customer_id=? AND used_at IS NULL",
            Integer.class,
            customerId
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isZero();
    }

    @Test
    void alreadyUsedAndExpiredTokensReturnTheSameGenericError() {
        Long customerId = createCustomer();
        String usedToken = "already-used-reset-token";
        String expiredToken = "expired-reset-token";
        insertUsedToken(customerId, usedToken);
        insertExpiredToken(customerId, expiredToken);

        assertGenericInvalidToken(() -> firstTransaction.executeWithoutResult(status ->
            firstService.resetPassword(usedToken, FIRST_PASSWORD)
        ));
        assertGenericInvalidToken(() -> secondTransaction.executeWithoutResult(status ->
            secondService.resetPassword(expiredToken, SECOND_PASSWORD)
        ));
        assertThat(authenticates(CURRENT_PASSWORD)).isTrue();
    }

    @RepeatedTest(3)
    void tokenCannotBeClaimedAfterItExpiresWhileWaitingForItsRowLock() throws Exception {
        Long customerId = createCustomer();
        String rawToken = "lock-wait-expiration-token";
        insertTokenExpiringAfter(customerId, rawToken, 2_500);
        insertSession(EMAIL);

        CountDownLatch tokenRowLocked = new CountDownLatch(1);
        CountDownLatch releaseTokenRow = new CountDownLatch(1);
        CountDownLatch resetAttemptedTokenLock = new CountDownLatch(1);
        PasswordResetStore observingStore = new TokenLockObservingPasswordResetStore(
            new JdbcTemplate(secondDataSource),
            resetAttemptedTokenLock
        );
        CustomerPasswordService observingService = service(
            passwordEncoder,
            sessions(secondDataSource),
            observingStore
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> lockHolder = executor.submit(() -> firstTransaction.executeWithoutResult(status -> {
            jdbc.queryForObject(
                "SELECT token_hash FROM customer_password_reset_tokens WHERE token_hash=? FOR UPDATE",
                String.class,
                hash(rawToken)
            );
            tokenRowLocked.countDown();
            await(releaseTokenRow, "Timed out waiting to release the expiring token row");
        }));
        Future<Throwable> reset = null;
        try {
            assertThat(tokenRowLocked.await(10, TimeUnit.SECONDS))
                .as("the independent transaction locked the token row")
                .isTrue();
            assertThat(tokenIsUnexpiredByDatabaseClock(rawToken))
                .as("the reset begins before database wall-clock expiry")
                .isTrue();

            reset = executor.submit(() -> resetOutcome(
                secondTransaction,
                observingService,
                rawToken,
                FIRST_PASSWORD
            ));
            assertThat(resetAttemptedTokenLock.await(10, TimeUnit.SECONDS))
                .as("resetPassword reached the token-row lock after locking the customer")
                .isTrue();
            awaitBlockedTokenRowLock();
            assertThat(tokenIsUnexpiredByDatabaseClock(rawToken))
                .as("PostgreSQL observed the reset waiting before token expiry")
                .isTrue();

            awaitTokenExpirationByDatabaseClock(rawToken);
            releaseTokenRow.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            assertGenericInvalidToken(reset.get(10, TimeUnit.SECONDS));
        } finally {
            releaseTokenRow.countDown();
            cancel(reset);
            cancel(lockHolder);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(authenticates(CURRENT_PASSWORD)).isTrue();
        assertThat(authenticates(FIRST_PASSWORD)).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT version FROM customers WHERE id=?",
            Long.class,
            customerId
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT used_at IS NULL FROM customer_password_reset_tokens WHERE token_hash=?",
            Boolean.class,
            hash(rawToken)
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isOne();
        assertGenericInvalidToken(() -> secondTransaction.executeWithoutResult(status ->
            secondService.resetPassword(rawToken, SECOND_PASSWORD)
        ));
    }

    @Test
    void tokenClaimedBeforeExpirationUsesDatabaseWallClockAndSucceeds() {
        Long customerId = createCustomer();
        String rawToken = "before-expiration-control-token";
        insertTokenExpiringAfter(customerId, rawToken, 30_000);
        insertSession(EMAIL);

        secondTransaction.executeWithoutResult(status ->
            secondService.resetPassword(rawToken, FIRST_PASSWORD)
        );

        assertThat(authenticates(FIRST_PASSWORD)).isTrue();
        assertThat(authenticates(CURRENT_PASSWORD)).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT version FROM customers WHERE id=?",
            Long.class,
            customerId
        )).isOne();
        assertThat(jdbc.queryForObject(
            "SELECT used_at IS NOT NULL AND used_at<expires_at "
                + "FROM customer_password_reset_tokens WHERE token_hash=?",
            Boolean.class,
            hash(rawToken)
        )).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            EMAIL
        )).isZero();
    }

    private static CustomerPasswordService service(
        HikariDataSource dataSource,
        PasswordEncoder encoder,
        CustomerSessionService customerSessions
    ) {
        return service(
            encoder,
            customerSessions,
            new PasswordResetStore(new JdbcTemplate(dataSource))
        );
    }

    private static CustomerPasswordService service(
        PasswordEncoder encoder,
        CustomerSessionService customerSessions,
        PasswordResetStore passwordResetStore
    ) {
        return new CustomerPasswordService(
            mock(CustomerRepository.class),
            mock(PasswordResetTokenRepository.class),
            encoder,
            mock(PasswordResetMailService.class),
            customerSessions,
            passwordResetStore,
            Duration.ofMinutes(30)
        );
    }

    private static CustomerSessionService sessions(HikariDataSource dataSource) {
        return new CustomerSessionService(
            mock(SecurityContextRepository.class),
            new JdbcTemplate(dataSource),
            Duration.ofDays(30)
        );
    }

    private ResetOutcome attemptReset(
        TransactionTemplate transaction,
        CustomerPasswordService service,
        String rawToken,
        String newPassword,
        CyclicBarrier start
    ) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            transaction.executeWithoutResult(status -> service.resetPassword(rawToken, newPassword));
            return new ResetOutcome(true, newPassword, null);
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            return new ResetOutcome(false, newPassword, exception.getReason());
        }
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
            "Race",
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

    private void insertUsedToken(Long customerId, String rawToken) {
        jdbc.update(
            """
            INSERT INTO customer_password_reset_tokens(
                customer_id,token_hash,expires_at,used_at,created_at
            ) VALUES(?,?,CURRENT_TIMESTAMP + INTERVAL '30 minutes',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,
            customerId,
            hash(rawToken)
        );
    }

    private void insertExpiredToken(Long customerId, String rawToken) {
        jdbc.update(
            """
            INSERT INTO customer_password_reset_tokens(
                customer_id,token_hash,expires_at,used_at,created_at
            ) VALUES(?,?,CURRENT_TIMESTAMP - INTERVAL '1 minute',NULL,CURRENT_TIMESTAMP)
            """,
            customerId,
            hash(rawToken)
        );
    }

    private void insertTokenExpiringAfter(Long customerId, String rawToken, int milliseconds) {
        jdbc.update(
            """
            INSERT INTO customer_password_reset_tokens(
                customer_id,token_hash,expires_at,used_at,created_at
            ) VALUES(?,?,clock_timestamp() + (? * INTERVAL '1 millisecond'),NULL,clock_timestamp())
            """,
            customerId,
            hash(rawToken),
            milliseconds
        );
    }

    private void insertSession(String principalName) {
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
            principalName
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

    private void assertGenericInvalidToken(Runnable request) {
        assertThatThrownBy(request::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo(GENERIC_INVALID_TOKEN);
            });
    }

    private void assertGenericInvalidToken(Throwable failure) {
        assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getReason()).isEqualTo(GENERIC_INVALID_TOKEN);
        });
    }

    private Throwable resetOutcome(
        TransactionTemplate transaction,
        CustomerPasswordService service,
        String rawToken,
        String newPassword
    ) {
        try {
            transaction.executeWithoutResult(status -> service.resetPassword(rawToken, newPassword));
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private boolean tokenIsUnexpiredByDatabaseClock(String rawToken) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT expires_at>clock_timestamp() FROM customer_password_reset_tokens WHERE token_hash=?",
            Boolean.class,
            hash(rawToken)
        ));
    }

    private void awaitTokenExpirationByDatabaseClock(String rawToken) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean expired = jdbc.queryForObject(
                "SELECT expires_at<=clock_timestamp() FROM customer_password_reset_tokens WHERE token_hash=?",
                Boolean.class,
                hash(rawToken)
            );
            if (Boolean.TRUE.equals(expired)) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Token did not expire according to the PostgreSQL wall clock");
    }

    private void awaitBlockedTokenRowLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_stat_activity
                WHERE datname=current_database()
                  AND cardinality(pg_blocking_pids(pid))>0
                  AND query ILIKE '%customer_password_reset_tokens%'
                  AND query ILIKE '%FOR UPDATE%'
                """,
                Integer.class
            );
            if (blocked != null && blocked > 0) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("PostgreSQL did not report resetPassword waiting for the token-row lock");
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

    private static HikariDataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(4);
        return dataSource;
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

    private record ResetOutcome(boolean succeeded, String password, String error) {
    }

    private static final class CoordinatedPasswordEncoder implements PasswordEncoder {
        private final PasswordEncoder delegate;
        private final AtomicInteger passwordChecks = new AtomicInteger();
        private final CountDownLatch firstPasswordCheck = new CountDownLatch(1);
        private final CountDownLatch releasePasswordCheck = new CountDownLatch(1);

        private CoordinatedPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            int check = passwordChecks.incrementAndGet();
            if (check == 1) {
                firstPasswordCheck.countDown();
            }
            try {
                if (!releasePasswordCheck.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release password verification");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Password verification was interrupted", exception);
            }
            return delegate.matches(rawPassword, encodedPassword);
        }
    }

    private static final class StoreStageCoordinator {
        private final CountDownLatch bothRequestsReached = new CountDownLatch(2);
    }

    private static final class CoordinatedPasswordResetStore extends PasswordResetStore {
        private final StoreStageCoordinator coordinator;

        private CoordinatedPasswordResetStore(JdbcTemplate jdbc, StoreStageCoordinator coordinator) {
            super(jdbc);
            this.coordinator = coordinator;
        }

        @Override
        public java.util.Optional<Long> findTokenCustomerId(String tokenHash) {
            java.util.Optional<Long> customerId = super.findTokenCustomerId(tokenHash);
            coordinator.bothRequestsReached.countDown();
            try {
                if (!coordinator.bothRequestsReached.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both reset transactions did not reach the token store");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Token-store synchronization was interrupted", exception);
            }
            return customerId;
        }
    }

    private static final class TokenLockObservingPasswordResetStore extends PasswordResetStore {
        private final CountDownLatch tokenLockAttempted;

        private TokenLockObservingPasswordResetStore(
            JdbcTemplate jdbc,
            CountDownLatch tokenLockAttempted
        ) {
            super(jdbc);
            this.tokenLockAttempted = tokenLockAttempted;
        }

        @Override
        public boolean lockTokenForClaim(String tokenHash) {
            tokenLockAttempted.countDown();
            return super.lockTokenForClaim(tokenHash);
        }
    }
}
