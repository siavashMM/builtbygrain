package com.builtbygrain.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.builtbygrain.backend.security.LoginThrottleService.Audience;
import com.builtbygrain.backend.security.RateLimitService.Decision;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class LoginThrottlePostgreSqlConcurrencyTest {

    private static final int ACCOUNT_LIMIT = 5;
    private static final int REQUEST_COUNT = 20;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static HikariDataSource firstDataSource;
    static HikariDataSource secondDataSource;
    static JdbcTemplate jdbc;
    static LoginThrottleService firstService;
    static LoginThrottleService secondService;
    static TransactionTemplate firstTransaction;
    static TransactionTemplate secondTransaction;

    @BeforeAll
    static void configureServices() {
        firstDataSource = dataSource();
        secondDataSource = dataSource();
        Flyway.configure()
            .dataSource(firstDataSource)
            .load()
            .migrate();

        jdbc = new JdbcTemplate(firstDataSource);
        RateLimitProperties properties = new RateLimitProperties();
        firstService = new LoginThrottleService(
            new RateLimitService(new JdbcTemplate(firstDataSource)),
            properties
        );
        secondService = new LoginThrottleService(
            new RateLimitService(new JdbcTemplate(secondDataSource)),
            properties
        );
        firstTransaction = new TransactionTemplate(new DataSourceTransactionManager(firstDataSource));
        secondTransaction = new TransactionTemplate(new DataSourceTransactionManager(secondDataSource));
    }

    @AfterAll
    static void closeDataSources() {
        if (firstDataSource != null) firstDataSource.close();
        if (secondDataSource != null) secondDataSource.close();
    }

    @BeforeEach
    void clearBuckets() {
        jdbc.update("DELETE FROM rate_limit_buckets");
    }

    @ParameterizedTest
    @EnumSource(Audience.class)
    void twoServiceInstancesShareOneAtomicAccountLimit(Audience audience) throws Exception {
        String identity = audience == Audience.ADMIN ? "missing-admin" : "missing@example.test";
        AtomicInteger verificationCalls = new AtomicInteger();
        CountDownLatch verificationStarted = new CountDownLatch(ACCOUNT_LIMIT);
        CountDownLatch releaseVerifications = new CountDownLatch(1);
        CountDownLatch throttled = new CountDownLatch(REQUEST_COUNT - ACCOUNT_LIMIT);
        CyclicBarrier start = new CyclicBarrier(REQUEST_COUNT + 1);
        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        List<Future<Decision>> decisions = new ArrayList<>();

        try {
            for (int requestNumber = 1; requestNumber <= REQUEST_COUNT; requestNumber++) {
                int attempt = requestNumber;
                decisions.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    LoginThrottleService service = attempt % 2 == 0 ? firstService : secondService;
                    TransactionTemplate transaction = attempt % 2 == 0 ? firstTransaction : secondTransaction;
                    Decision decision = transaction.execute(status -> service.reserveAttempt(
                        audience,
                        identity,
                        "198.51.100." + attempt
                    ));
                    if (decision == null) throw new AssertionError("Reservation returned no decision");
                    if (decision.allowed()) {
                        verificationCalls.incrementAndGet();
                        verificationStarted.countDown();
                        if (!releaseVerifications.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to release password verification");
                        }
                    } else {
                        throttled.countDown();
                    }
                    return decision;
                }));
            }

            start.await(10, TimeUnit.SECONDS);
            assertThat(verificationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(throttled.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(verificationCalls).hasValue(ACCOUNT_LIMIT);
            releaseVerifications.countDown();

            List<Decision> results = new ArrayList<>();
            for (Future<Decision> decision : decisions) {
                results.add(decision.get(10, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(Decision::allowed).hasSize(ACCOUNT_LIMIT);
            assertThat(jdbc.queryForObject(
                "SELECT request_count FROM rate_limit_buckets WHERE policy=?",
                Integer.class,
                audience == Audience.ADMIN ? "ADMIN_LOGIN_ACCOUNT" : "CUSTOMER_LOGIN_ACCOUNT"
            )).isEqualTo(ACCOUNT_LIMIT);
        } finally {
            releaseVerifications.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static HikariDataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(12);
        return dataSource;
    }
}
