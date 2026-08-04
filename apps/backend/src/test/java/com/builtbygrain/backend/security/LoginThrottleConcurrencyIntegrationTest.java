package com.builtbygrain.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.builtbygrain.backend.customer.Customer;
import com.builtbygrain.backend.customer.CustomerRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class LoginThrottleConcurrencyIntegrationTest {

    private static final int ACCOUNT_LIMIT = 5;
    private static final int REQUEST_COUNT = 20;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean AuthenticationManager authenticationManager;

    @ParameterizedTest(name = "{0} limits {2}")
    @CsvSource({
        "/api/admin/auth/login,username,admin,Unauthorized,'Too many login attempts'",
        "/api/admin/auth/login,username,missing-admin,Unauthorized,'Too many login attempts'",
        "/api/account/auth/login,email,customer@example.test,'Email or password is incorrect','Too many attempts. Please try again later.'",
        "/api/account/auth/login,email,missing@example.test,'Email or password is incorrect','Too many attempts. Please try again later.'"
    })
    void concurrentLoginNeverStartsMorePasswordVerificationsThanTheAccountLimit(
        String endpoint,
        String identityField,
        String identity,
        String genericError,
        String genericThrottleError
    ) throws Exception {
        jdbc.update("DELETE FROM rate_limit_buckets");
        ensureCustomerExists();
        reset(authenticationManager);

        AtomicInteger verificationCalls = new AtomicInteger();
        CountDownLatch verificationStarted = new CountDownLatch(ACCOUNT_LIMIT);
        CountDownLatch releaseVerifications = new CountDownLatch(1);
        CountDownLatch throttledResponses = new CountDownLatch(REQUEST_COUNT - ACCOUNT_LIMIT);
        when(authenticationManager.authenticate(any())).thenAnswer(invocation -> {
            verificationCalls.incrementAndGet();
            verificationStarted.countDown();
            if (!releaseVerifications.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release password verification");
            }
            throw new BadCredentialsException("Bad credentials");
        });

        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        CyclicBarrier start = new CyclicBarrier(REQUEST_COUNT + 1);
        List<Future<MvcResult>> responses = new ArrayList<>();

        try {
            for (int requestNumber = 1; requestNumber <= REQUEST_COUNT; requestNumber++) {
                int addressSuffix = requestNumber;
                responses.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    MvcResult result = mockMvc.perform(post(endpoint)
                            .with(csrf())
                            .with(request -> {
                                request.setRemoteAddr("198.51.100." + addressSuffix);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                identityField, identity,
                                "password", "wrong password"
                            ))))
                        .andReturn();
                    if (result.getResponse().getStatus() == 429) {
                        throttledResponses.countDown();
                    }
                    return result;
                }));
            }

            start.await(10, TimeUnit.SECONDS);
            assertThat(verificationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(throttledResponses.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(verificationCalls).hasValue(ACCOUNT_LIMIT);
            releaseVerifications.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> response : responses) {
                results.add(response.get(10, TimeUnit.SECONDS));
            }

            assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 401))
                .hasSize(ACCOUNT_LIMIT)
                .allSatisfy(result -> assertThat(result.getResponse().getContentAsString())
                    .isEqualTo("{\"error\":\"" + genericError + "\"}"));
            assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 429))
                .hasSize(REQUEST_COUNT - ACCOUNT_LIMIT)
                .allSatisfy(result -> {
                    assertThat(result.getResponse().getContentAsString())
                        .isEqualTo("{\"error\":\"" + genericThrottleError + "\"}");
                    assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
                });
            assertThat(verificationCalls).hasValue(ACCOUNT_LIMIT);
        } finally {
            releaseVerifications.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void ensureCustomerExists() {
        if (customers.existsByNormalizedEmail("customer@example.test")) return;
        customers.saveAndFlush(new Customer(
            "customer@example.test",
            "customer@example.test",
            passwordEncoder.encode("correct horse grain"),
            "Test",
            "Customer",
            "en"
        ));
    }
}
