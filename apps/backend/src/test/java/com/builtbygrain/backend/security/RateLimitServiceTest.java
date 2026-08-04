package com.builtbygrain.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class RateLimitServiceTest {

    @Autowired RateLimitService rateLimits;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearBuckets() {
        jdbc.update("DELETE FROM rate_limit_buckets");
    }

    @Test
    void concurrentRequestsCannotExceedTheBucketLimit() throws Exception {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(5, Duration.ofMinutes(1));
        RateLimitService.Rule rule = new RateLimitService.Rule("CONCURRENT_TEST", "same-subject", limit);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int request = 0; request < 20; request++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return rateLimits.consume(List.of(rule)).allowed();
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) allowed++;
            }

            assertThat(allowed).isEqualTo(5);
            assertThat(jdbc.queryForObject(
                "SELECT request_count FROM rate_limit_buckets WHERE policy='CONCURRENT_TEST'",
                Integer.class
            )).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void blockedDecisionsIncludeRetryDelayAndResetOnlyTheSelectedScope() {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, Duration.ofMinutes(1));
        RateLimitService.Rule account = new RateLimitService.Rule("ACCOUNT_TEST", "account", limit);
        RateLimitService.Rule ip = new RateLimitService.Rule("IP_TEST", "address", limit);

        assertThat(rateLimits.consume(List.of(account, ip)).allowed()).isTrue();
        RateLimitService.Decision blocked = rateLimits.consume(List.of(account, ip));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);

        rateLimits.reset("ACCOUNT_TEST", "account");
        assertThat(rateLimits.check(List.of(account)).allowed()).isTrue();
        assertThat(rateLimits.check(List.of(ip)).allowed()).isFalse();
    }

    @Test
    void rejectedRuleSetDoesNotConsumeAnyAvailableBucket() {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, Duration.ofMinutes(1));
        RateLimitService.Rule blockedAccount = new RateLimitService.Rule("FULL_ACCOUNT", "account", limit);
        RateLimitService.Rule availableIp = new RateLimitService.Rule("AVAILABLE_IP", "address", limit);

        assertThat(rateLimits.consume(List.of(blockedAccount)).allowed()).isTrue();
        assertThat(rateLimits.consume(List.of(blockedAccount, availableIp)).allowed()).isFalse();

        assertThat(rateLimits.check(List.of(blockedAccount)).allowed()).isFalse();
        assertThat(rateLimits.check(List.of(availableIp)).allowed()).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT request_count FROM rate_limit_buckets WHERE policy='AVAILABLE_IP'",
            Integer.class
        )).isZero();
    }

    @Test
    void ipv6ClientsAreGroupedByTheirNetworkPrefix() {
        assertThat(ClientAddress.normalize("2001:db8:1234:5678::1"))
            .isEqualTo(ClientAddress.normalize("2001:db8:1234:5678:ffff::99"))
            .endsWith("/64");
        assertThat(ClientAddress.normalize("198.51.100.10")).isEqualTo("198.51.100.10");
    }
}
