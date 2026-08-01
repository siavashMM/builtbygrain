package com.builtbygrain.backend.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class CustomerSocialAccountServiceTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerSocialIdentityRepository identities = mock(CustomerSocialIdentityRepository.class);
    private final CustomerSocialAccountService socialAccounts =
        new CustomerSocialAccountService(customers, identities);

    @Test
    void linksVerifiedGoogleIdentityToExistingPasswordCustomerInsteadOfCreatingDuplicate() {
        Customer existing = new Customer(
            "Owner@Example.test",
            "owner@example.test",
            "{bcrypt}existing-password-hash",
            "Avery",
            "Oak",
            "en"
        );
        OidcUser googleUser = mock(OidcUser.class);
        when(googleUser.getSubject()).thenReturn("stable-google-subject");
        when(googleUser.getEmail()).thenReturn("  OWNER@example.test ");
        when(googleUser.getEmailVerified()).thenReturn(true);
        when(identities.findByProviderAndProviderSubject("google", "stable-google-subject"))
            .thenReturn(Optional.empty());
        when(customers.findByNormalizedEmail("owner@example.test")).thenReturn(Optional.of(existing));

        Customer provisioned = socialAccounts.provision("GOOGLE", googleUser);

        assertThat(provisioned).isSameAs(existing);
        assertThat(provisioned.getPasswordHash()).isEqualTo("{bcrypt}existing-password-hash");
        assertThat(provisioned.getEmailVerifiedAt()).isNotNull();
        verify(customers, never()).saveAndFlush(any(Customer.class));

        ArgumentCaptor<CustomerSocialIdentity> linkedIdentity =
            ArgumentCaptor.forClass(CustomerSocialIdentity.class);
        verify(identities).saveAndFlush(linkedIdentity.capture());
        assertThat(linkedIdentity.getValue().getCustomer()).isSameAs(existing);
    }
}
