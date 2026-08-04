package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutIntegrationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ClientRegistrationRepository registrations;

    @Test
    void reportsOptionalIntegrationsHonestlyAndKeepsUnconfiguredProvidersClosed() throws Exception {
        mockMvc.perform(get("/api/public/checkout/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.socialProviders.google").value(false))
            .andExpect(jsonPath("$.socialProviders.apple").value(false))
            .andExpect(jsonPath("$.addressAutocompleteEnabled").value(false))
            .andExpect(jsonPath("$.googleMapsBrowserKey").doesNotExist());

        mockMvc.perform(get("/api/account/auth/social/google")
                .queryParam("returnUrl", "/checkout/delivery"))
            .andExpect(status().isNotFound());
    }

    @Test
    void buildsAnOidcGoogleRegistrationOnlyWhenBothCredentialsExist() {
        SocialLoginConfiguration configuration = new SocialLoginConfiguration();
        ClientRegistrationRepository configured = configuration.clientRegistrationRepository(
            "google-client", "google-secret", "", ""
        );

        assertThat(configured.findByRegistrationId("google")).isNotNull();
        assertThat(configured.findByRegistrationId("google").getRedirectUri())
            .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(configured.findByRegistrationId("apple")).isNull();
        assertThat(registrations.findByRegistrationId("google")).isNull();
    }

    @Test
    void returnsSocialFailuresToTheFlowThatStartedAuthentication() {
        assertThat(CheckoutIntegrationController.socialFailureUrl("/account/security"))
            .isEqualTo("/account/sign-in?socialError=failed&returnUrl=%2Faccount%2Fsecurity");
        assertThat(CheckoutIntegrationController.socialFailureUrl("/checkout/delivery"))
            .isEqualTo("/checkout/account?socialError=failed&returnUrl=%2Fcheckout%2Fdelivery");
        assertThat(CheckoutIntegrationController.socialFailureUrl("https://attacker.example"))
            .isEqualTo("/account/sign-in?socialError=failed&returnUrl=%2Faccount");
    }
}
