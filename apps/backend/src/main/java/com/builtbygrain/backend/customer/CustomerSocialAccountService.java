package com.builtbygrain.backend.customer;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerSocialAccountService {

    private final CustomerRepository customers;
    private final CustomerSocialIdentityRepository identities;

    public CustomerSocialAccountService(
        CustomerRepository customers,
        CustomerSocialIdentityRepository identities
    ) {
        this.customers = customers;
        this.identities = identities;
    }

    @Transactional
    public Customer provision(String provider, OidcUser user) {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        String subject = requiredClaim(user.getSubject(), "The identity provider did not return a subject.");
        Customer linked = identities.findByProviderAndProviderSubject(normalizedProvider, subject)
            .map(CustomerSocialIdentity::getCustomer)
            .orElse(null);
        if (linked != null) {
            if (!linked.isActive()) throw unauthorized();
            return linked;
        }

        String email = requiredClaim(user.getEmail(), "The identity provider did not share an email address.");
        boolean verified = "apple".equals(normalizedProvider) || Boolean.TRUE.equals(user.getEmailVerified());
        if (!verified) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The identity provider did not verify the email address.");
        }

        String normalizedEmail = CustomerAccountService.normalizeEmail(email);
        Customer customer = customers.findByNormalizedEmail(normalizedEmail).orElseGet(() -> {
            String givenName = clean(user.getGivenName());
            String familyName = clean(user.getFamilyName());
            Customer created = new Customer(
                email.trim(),
                normalizedEmail,
                null,
                givenName == null ? friendlyName(email) : givenName,
                familyName == null ? "Customer" : familyName,
                "en"
            );
            return customers.saveAndFlush(created);
        });
        if (!customer.isActive()) throw unauthorized();
        customer.markEmailVerified();

        try {
            identities.saveAndFlush(new CustomerSocialIdentity(customer, normalizedProvider, subject));
        } catch (DataIntegrityViolationException exception) {
            return identities.findByProviderAndProviderSubject(normalizedProvider, subject)
                .map(CustomerSocialIdentity::getCustomer)
                .orElseThrow(() -> exception);
        }
        return customer;
    }

    private static String requiredClaim(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String friendlyName(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart.isBlank() ? "New" : localPart.substring(0, Math.min(localPart.length(), 100));
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This customer account is unavailable.");
    }
}
