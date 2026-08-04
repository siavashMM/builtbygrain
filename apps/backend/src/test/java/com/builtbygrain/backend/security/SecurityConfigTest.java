package com.builtbygrain.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimits;
    @Autowired RateLimitProperties rateLimitProperties;

    @BeforeEach
    void clearSecurityState() {
        jdbc.update("DELETE FROM rate_limit_buckets");
        jdbc.update("DELETE FROM admin_login_attempts");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update(
            "UPDATE admin_accounts SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE username=?",
            passwordEncoder.encode("admin"),
            "admin"
        );
    }

    @Test
    void publicEndpointsRemainPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/public/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void corsAllowsOnlyTheConfiguredLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/public/products")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/public/products")
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
            .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointIssuesAngularCompatibleCookie() throws Exception {
        mockMvc.perform(get("/api/admin/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void productionCookieConfigurationIsSecureAndAngularCompatible() {
        SecurityConfig configuration = new SecurityConfig();
        MockHttpServletRequest request = new MockHttpServletRequest();

        MockHttpServletResponse sessionResponse = new MockHttpServletResponse();
        CookieSerializer sessionCookie = configuration.adminSessionCookie(true, "Lax");
        sessionCookie.writeCookieValue(new CookieValue(request, sessionResponse, "session-id"));
        assertThat(sessionResponse.getHeader(HttpHeaders.SET_COOKIE))
            .contains("BBG_ADMIN_SESSION=", "Path=/", "Secure", "HttpOnly", "SameSite=Lax");

        MockHttpServletResponse csrfResponse = new MockHttpServletResponse();
        var csrfRepository = configuration.csrfTokenRepository(true);
        csrfRepository.saveToken(csrfRepository.generateToken(request), request, csrfResponse);
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
        assertThat(csrfCookie.getSecure()).isTrue();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void adminLoginCreatesServerSessionAndSessionAuthenticatesSubsequentRequests() throws Exception {
        AuthenticatedAdmin admin = login("admin", "admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session", Integer.class)).isOne();

        mockMvc.perform(get("/api/admin/auth/session").cookie(admin.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.admin").value(true));

        mockMvc.perform(get("/api/admin/products").cookie(admin.session()))
            .andExpect(status().isOk());
    }

    @Test
    void expiredJdbcSessionIsRejected() throws Exception {
        AuthenticatedAdmin admin = login("admin", "admin");
        jdbc.update("UPDATE spring_session SET last_access_time=0,max_inactive_interval=1,expiry_time=1");

        mockMvc.perform(get("/api/admin/auth/session").cookie(admin.session()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void basicAuthenticationIsDisabledAndFailuresAreGeneric() throws Exception {
        mockMvc.perform(get("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

        Csrf csrf = csrf();
        mockMvc.perform(post("/api/admin/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"missing\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void mutatingAdminRequestsRequireCsrfAndLogoutInvalidatesTheSession() throws Exception {
        AuthenticatedAdmin admin = login("admin", "admin");

        mockMvc.perform(post("/api/admin/auth/logout").cookie(admin.session()))
            .andExpect(status().isForbidden());

        Csrf csrf = csrf(admin.session());
        mockMvc.perform(post("/api/admin/auth/logout")
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", "mismatched"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/catalog/reset")
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/auth/logout")
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/auth/session").cookie(admin.session()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRequiresCurrentPasswordAndInvalidatesEveryAdminSession() throws Exception {
        AuthenticatedAdmin changingSession = login("admin", "admin");
        AuthenticatedAdmin otherSession = login("admin", "admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session", Integer.class)).isEqualTo(2);

        Csrf csrf = csrf(changingSession.session());
        mockMvc.perform(post("/api/admin/auth/password")
                .cookie(changingSession.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"admin","newPassword":"new-secure-password"}
                    """))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session", Integer.class)).isZero();
        mockMvc.perform(get("/api/admin/auth/session").cookie(otherSession.session()))
            .andExpect(status().isUnauthorized());

        loginExpecting("admin", "admin", HttpStatus.UNAUTHORIZED);
        login("admin", "new-secure-password");
    }

    @Test
    void passwordChangeRejectsIncorrectCurrentAndWeakOrReusedPasswords() throws Exception {
        AuthenticatedAdmin admin = login("admin", "admin");
        Csrf csrf = csrf(admin.session());

        mockMvc.perform(post("/api/admin/auth/password")
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"wrong","newPassword":"new-secure-password"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Current password is incorrect"));

        mockMvc.perform(post("/api/admin/auth/password")
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"admin","newPassword":"too-short"}
                    """))
            .andExpect(status().isBadRequest());

        jdbc.update(
            "UPDATE admin_accounts SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE username=?",
            passwordEncoder.encode("current-admin-password"),
            "admin"
        );
        mockMvc.perform(post("/api/admin/auth/password")
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "currentPassword":"current-admin-password",
                      "newPassword":"current-admin-password"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("New password must be different from the current password"));

        mockMvc.perform(get("/api/admin/auth/session").cookie(admin.session()))
            .andExpect(status().isOk());
    }

    @Test
    void accountLimitAppliesAcrossDifferentIpAddresses() throws Exception {
        Csrf csrf = csrf();
        for (int attempt = 1; attempt <= 5; attempt++) {
            int addressSuffix = attempt;
            mockMvc.perform(post("/api/admin/auth/login")
                    .with(request -> {
                        request.setRemoteAddr("198.51.100." + addressSuffix);
                        return request;
                    })
                    .cookie(csrf.cookie())
                    .header("X-XSRF-TOKEN", csrf.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/admin/auth/login")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.6");
                    return request;
                })
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void ipLimitAppliesAcrossDifferentAccountNames() throws Exception {
        Csrf csrf = csrf();
        for (int attempt = 1; attempt <= 10; attempt++) {
            mockMvc.perform(post("/api/admin/auth/login")
                    .cookie(csrf.cookie())
                    .header("X-XSRF-TOKEN", csrf.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"unknown" + attempt + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        mockMvc.perform(post("/api/admin/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unknown11\",\"password\":\"wrong\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertThat(jdbc.queryForObject(
            "SELECT request_count FROM rate_limit_buckets WHERE policy='ADMIN_LOGIN_IP'",
            Integer.class
        )).isEqualTo(10);
    }

    @Test
    void successfulLoginResetsOnlyTheAccountBucketAndKeepsTheIpReservation() throws Exception {
        String address = "203.0.113.42";
        for (int attempt = 1; attempt <= 9; attempt++) {
            assertThat(rateLimits.consume(java.util.List.of(new RateLimitService.Rule(
                "ADMIN_LOGIN_IP",
                address,
                rateLimitProperties.getAdminLoginIp()
            ))).allowed()).isTrue();
        }

        AuthenticatedAdmin admin = login("admin", "admin", address);
        assertThat(admin.session().getValue()).isNotBlank();
        assertThat(rateLimits.check(java.util.List.of(new RateLimitService.Rule(
            "ADMIN_LOGIN_IP",
            address,
            rateLimitProperties.getAdminLoginIp()
        ))).allowed()).isFalse();
        assertThat(rateLimits.check(java.util.List.of(new RateLimitService.Rule(
            "ADMIN_LOGIN_ACCOUNT",
            "admin",
            rateLimitProperties.getAdminLoginAccount()
        ))).allowed()).isTrue();
    }

    @Test
    void authenticatedAdminUploadsUseWriteAndUploadPolicies() throws Exception {
        AuthenticatedAdmin admin = login("admin", "admin");
        Csrf csrf = csrf(admin.session());
        RateLimitService.Rule uploadRule = new RateLimitService.Rule(
            ApiRateLimitFilter.ADMIN_UPLOAD_POLICY,
            "admin",
            rateLimitProperties.getAdminUpload()
        );
        for (int attempt = 0; attempt < rateLimitProperties.getAdminUpload().getAttempts(); attempt++) {
            assertThat(rateLimits.consume(java.util.List.of(uploadRule)).allowed()).isTrue();
        }

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/admin/products/1/images")
                .file("images", "image".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .cookie(admin.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }

    private AuthenticatedAdmin login(String username, String password) throws Exception {
        return login(username, password, null);
    }

    private AuthenticatedAdmin login(String username, String password, String remoteAddress) throws Exception {
        Csrf csrf = csrf();
        MockHttpServletRequestBuilder request = post("/api/admin/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Credentials(username, password)));
        if (remoteAddress != null) {
            request.with(mockRequest -> {
                mockRequest.setRemoteAddr(remoteAddress);
                return mockRequest;
            });
        }
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
        String sessionCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(value -> value.startsWith("BBG_ADMIN_SESSION="))
            .findFirst().orElseThrow(() -> new AssertionError("Session cookie was not issued"));
        assertThat(sessionCookie).contains("Path=/", "HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        String value = sessionCookie.substring("BBG_ADMIN_SESSION=".length(), sessionCookie.indexOf(';'));
        return new AuthenticatedAdmin(new Cookie("BBG_ADMIN_SESSION", value));
    }

    private void loginExpecting(String username, String password, HttpStatus expectedStatus) throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/admin/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Credentials(username, password))))
            .andExpect(status().is(expectedStatus.value()));
    }

    private Csrf csrf(Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/admin/auth/csrf");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            for (Cookie existing : cookies) {
                if ("XSRF-TOKEN".equals(existing.getName())) {
                    cookie = existing;
                    break;
                }
            }
        }
        return new Csrf(cookie, body.get("token").asText());
    }

    private record Credentials(String username, String password) { }
    private record AuthenticatedAdmin(Cookie session) { }
    private record Csrf(Cookie cookie, String token) { }
}
