package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerAccountIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;

    @MockitoBean PasswordResetMailService mail;

    @BeforeEach
    void clearCustomerData() {
        jdbc.update("DELETE FROM rate_limit_buckets");
        jdbc.update("DELETE FROM customer_auth_requests");
        jdbc.update("DELETE FROM admin_login_attempts");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM customer_password_reset_tokens");
        jdbc.update("DELETE FROM customer_addresses");
        jdbc.update("DELETE FROM customer_social_identities");
        jdbc.update("DELETE FROM customers");
        clearInvocations(mail);
    }

    @Test
    void registrationValidatesNormalizesHashesAndCreatesPersistentSession() throws Exception {
        Csrf csrf = csrf();
        MvcResult registration = mockMvc.perform(post("/api/account/auth/register")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email":"  Rowan.Wood@Example.test ",
                      "password":"correct horse grain",
                      "firstName":"Rowan",
                      "lastName":"Wood",
                      "locale":"en",
                      "returnUrl":"/cart?step=identity"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customer.email").value("Rowan.Wood@Example.test"))
            .andExpect(jsonPath("$.customer.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.returnUrl").value("/cart?step=identity"))
            .andExpect(cookie().exists("BBG_ADMIN_SESSION"))
            .andReturn();

        assertThat(jdbc.queryForObject(
            "SELECT normalized_email FROM customers", String.class
        )).isEqualTo("rowan.wood@example.test");
        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM customers", String.class);
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain("correct horse grain");
        String sessionCookie = registration.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(value -> value.startsWith("BBG_ADMIN_SESSION="))
            .findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly", "SameSite=Lax", "Max-Age=2592000");
    }

    @Test
    void duplicateAndInvalidRegistrationAreSafeAndExternalReturnUrlsAreRejected() throws Exception {
        register("owner@example.test", "correct horse grain", "/account");
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/account/auth/register")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"OWNER@example.test","password":"another safe password",
                     "firstName":"Other","lastName":"Person","locale":"en","returnUrl":"https://attacker.example"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("An account already exists for this email address."));

        Csrf validationCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/register")
                .cookie(validationCsrf.cookie())
                .header("X-XSRF-TOKEN", validationCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"not-an-email","password":"short","firstName":"","lastName":"Person","locale":"en"}
                    """))
            .andExpect(status().isBadRequest());

        assertThat(CustomerAuthController.safeReturnUrl("//attacker.example")).isEqualTo("/account");
        assertThat(CustomerAuthController.safeReturnUrl("/\\attacker.example")).isEqualTo("/account");
        assertThat(CustomerAuthController.safeReturnUrl("/cart")).isEqualTo("/cart");
    }

    @Test
    void loginPersistsLogoutInvalidatesAndProtectedEndpointsRequireCustomerSession() throws Exception {
        AuthenticatedCustomer registered = register("returning@example.test", "correct horse grain", "/account");

        mockMvc.perform(get("/api/account/profile").cookie(registered.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("returning@example.test"));

        mockMvc.perform(get("/api/account/profile"))
            .andExpect(status().isUnauthorized());

        AuthenticatedCustomer loggedIn = login("RETURNING@example.test", "correct horse grain");
        Csrf csrf = csrf(loggedIn.session());
        mockMvc.perform(post("/api/account/auth/logout")
                .cookie(loggedIn.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/account/profile").cookie(loggedIn.session()))
            .andExpect(status().isUnauthorized());

        loginExpecting("returning@example.test", "wrong password", 401);
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndInvalidatesEveryCustomerSession() throws Exception {
        AuthenticatedCustomer first = register("secure@example.test", "correct horse grain", "/account");
        AuthenticatedCustomer second = login("secure@example.test", "correct horse grain");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE principal_name=?",
            Integer.class,
            "secure@example.test"
        )).isEqualTo(2);

        Csrf wrongCsrf = csrf(first.session());
        mockMvc.perform(post("/api/account/auth/password")
                .cookie(first.session(), wrongCsrf.cookie())
                .header("X-XSRF-TOKEN", wrongCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"wrong password","newPassword":"new correct horse grain"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Current password is incorrect"));

        Csrf csrf = csrf(first.session());
        mockMvc.perform(post("/api/account/auth/password")
                .cookie(first.session(), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"correct horse grain","newPassword":"new correct horse grain"}
                    """))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/account/profile").cookie(second.session()))
            .andExpect(status().isUnauthorized());
        loginExpecting("secure@example.test", "correct horse grain", 401);
        login("secure@example.test", "new correct horse grain");
    }

    @Test
    void profileAndAddressesAreOwnedAndDefaultsRemainExplicit() throws Exception {
        AuthenticatedCustomer owner = register("address-owner@example.test", "correct horse grain", "/account");
        AuthenticatedCustomer other = register("other-owner@example.test", "correct horse grain", "/account");

        Csrf ownerCsrf = csrf(owner.session());
        MvcResult created = mockMvc.perform(post("/api/account/addresses")
                .cookie(owner.session(), ownerCsrf.cookie())
                .header("X-XSRF-TOKEN", ownerCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("Oak Street", "12", false, false)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.defaultShipping").value(true))
            .andExpect(jsonPath("$.defaultBilling").value(true))
            .andReturn();
        long ownerAddressId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        Csrf secondCsrf = csrf(owner.session());
        MvcResult second = mockMvc.perform(post("/api/account/addresses")
                .cookie(owner.session(), secondCsrf.cookie())
                .header("X-XSRF-TOKEN", secondCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("Beech Lane", "4A", true, false)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.defaultShipping").value(true))
            .andReturn();
        long secondAddressId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE customer_id=(SELECT id FROM customers WHERE normalized_email=?) AND default_shipping=TRUE",
            Integer.class,
            "address-owner@example.test"
        )).isOne();

        Csrf otherCsrf = csrf(other.session());
        mockMvc.perform(put("/api/account/addresses/{id}", ownerAddressId)
                .cookie(other.session(), otherCsrf.cookie())
                .header("X-XSRF-TOKEN", otherCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("Changed", "1", false, false)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/account/addresses/{id}", ownerAddressId)
                .cookie(other.session(), otherCsrf.cookie())
                .header("X-XSRF-TOKEN", otherCsrf.token()))
            .andExpect(status().isNotFound());

        Csrf defaultCsrf = csrf(owner.session());
        mockMvc.perform(post("/api/account/addresses/{id}/default-billing", secondAddressId)
                .cookie(owner.session(), defaultCsrf.cookie())
                .header("X-XSRF-TOKEN", defaultCsrf.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultBilling").value(true));

        Csrf profileCsrf = csrf(owner.session());
        mockMvc.perform(put("/api/account/profile")
                .cookie(owner.session(), profileCsrf.cookie())
                .header("X-XSRF-TOKEN", profileCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"new-owner@example.test","firstName":"Avery","lastName":"Oak",
                     "phone":"+49 30 555 0100","locale":"de"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("+49 30 555 0100"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void forgotPasswordIsNonEnumeratingAndResetTokenExpiresAndWorksOnlyOnce() throws Exception {
        register("reset@example.test", "correct horse grain", "/account");
        jdbc.update("DELETE FROM spring_session");

        Csrf unknownCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/forgot-password")
                .cookie(unknownCsrf.cookie())
                .header("X-XSRF-TOKEN", unknownCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.test\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(
                "If an account exists for that email address, a reset link has been sent."));
        verify(mail, never()).send(any(), any());

        Csrf knownCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/forgot-password")
                .cookie(knownCsrf.cookie())
                .header("X-XSRF-TOKEN", knownCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.test\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(
                "If an account exists for that email address, a reset link has been sent."));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mail).send(any(Customer.class), tokenCaptor.capture());
        String token = tokenCaptor.getValue();
        assertThat(token).hasSizeGreaterThanOrEqualTo(32);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM customer_password_reset_tokens", String.class))
            .doesNotContain(token);

        Csrf resetCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/reset-password")
                .cookie(resetCsrf.cookie())
                .header("X-XSRF-TOKEN", resetCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Reset(token, "new correct horse grain"))))
            .andExpect(status().isOk());
        loginExpecting("reset@example.test", "correct horse grain", 401);
        login("reset@example.test", "new correct horse grain");

        Csrf reusedCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/reset-password")
                .cookie(reusedCsrf.cookie())
                .header("X-XSRF-TOKEN", reusedCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Reset(token, "third correct horse grain"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("This password reset link is invalid or has expired"));

        clearInvocations(mail);
        requestReset("reset@example.test");
        ArgumentCaptor<String> expiredCaptor = ArgumentCaptor.forClass(String.class);
        verify(mail).send(any(Customer.class), expiredCaptor.capture());
        jdbc.update("UPDATE customer_password_reset_tokens SET expires_at=DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE used_at IS NULL");
        Csrf expiredCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/reset-password")
                .cookie(expiredCsrf.cookie())
                .header("X-XSRF-TOKEN", expiredCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Reset(expiredCaptor.getValue(), "fourth correct horse grain"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void socialOnlyCustomerCanCreateAnOptionalPasswordThroughTheVerifiedEmailFlow() throws Exception {
        Customer socialOnly = new Customer(
            "social@example.test",
            "social@example.test",
            null,
            "Social",
            "Customer",
            "en"
        );
        socialOnly.markEmailVerified();
        customers.saveAndFlush(socialOnly);

        mockMvc.perform(get("/api/account/profile")
                .with(user("social@example.test").roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.passwordSet").value(false));

        requestReset("social@example.test");
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mail).send(any(Customer.class), tokenCaptor.capture());

        Csrf resetCsrf = csrf();
        mockMvc.perform(post("/api/account/auth/reset-password")
                .cookie(resetCsrf.cookie())
                .header("X-XSRF-TOKEN", resetCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new Reset(tokenCaptor.getValue(), "optional social password")
                )))
            .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
            "SELECT password_hash FROM customers WHERE normalized_email=?",
            String.class,
            "social@example.test"
        )).startsWith("{bcrypt}");
        login("social@example.test", "optional social password");
    }

    private AuthenticatedCustomer register(String email, String password, String returnUrl) throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/account/auth/register")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Registration(
                    email, password, "Avery", "Oak", "en", returnUrl
                ))))
            .andExpect(status().isCreated())
            .andReturn();
        return new AuthenticatedCustomer(sessionCookie(result));
    }

    private AuthenticatedCustomer login(String email, String password) throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/account/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Login(email, password, "/account"))))
            .andExpect(status().isOk())
            .andReturn();
        return new AuthenticatedCustomer(sessionCookie(result));
    }

    private void loginExpecting(String email, String password, int status) throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/account/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Login(email, password, "/account"))))
            .andExpect(status().is(status));
    }

    private void requestReset(String email) throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/account/auth/forgot-password")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("email", email))))
            .andExpect(status().isAccepted());
    }

    private Csrf csrf(Cookie... existingCookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/account/auth/csrf");
        if (existingCookies.length > 0) request.cookie(existingCookies);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            for (Cookie existing : existingCookies) {
                if ("XSRF-TOKEN".equals(existing.getName())) cookie = existing;
            }
        }
        return new Csrf(cookie, response.get("token").asText());
    }

    private Cookie sessionCookie(MvcResult result) {
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(value -> value.startsWith("BBG_ADMIN_SESSION="))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Session cookie was not issued"));
        String value = header.substring("BBG_ADMIN_SESSION=".length(), header.indexOf(';'));
        return new Cookie("BBG_ADMIN_SESSION", value);
    }

    private String addressJson(String street, String houseNumber, boolean shipping, boolean billing) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.ofEntries(
            java.util.Map.entry("recipientName", "Avery Oak"),
            java.util.Map.entry("street", street),
            java.util.Map.entry("houseNumber", houseNumber),
            java.util.Map.entry("postalCode", "10115"),
            java.util.Map.entry("city", "Berlin"),
            java.util.Map.entry("countryCode", "DE"),
            java.util.Map.entry("defaultShipping", shipping),
            java.util.Map.entry("defaultBilling", billing)
        ));
    }

    private record Registration(String email, String password, String firstName, String lastName, String locale, String returnUrl) { }
    private record Login(String email, String password, String returnUrl) { }
    private record Reset(String token, String newPassword) { }
    private record AuthenticatedCustomer(Cookie session) { }
    private record Csrf(Cookie cookie, String token) { }
}
