package com.builtbygrain.backend.customer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@Configuration
public class SocialLoginConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
        @Value("${app.oauth.google.client-id:}") String googleClientId,
        @Value("${app.oauth.google.client-secret:}") String googleClientSecret,
        @Value("${app.oauth.apple.client-id:}") String appleClientId,
        @Value("${app.oauth.apple.client-secret:}") String appleClientSecret
    ) {
        Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
        if (hasText(googleClientId) && hasText(googleClientSecret)) {
            registrations.put("google", ClientRegistration.withRegistrationId("google")
                .clientId(googleClientId.trim())
                .clientSecret(googleClientSecret.trim())
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .build());
        }
        if (hasText(appleClientId) && hasText(appleClientSecret)) {
            registrations.put("apple", ClientRegistrations.fromIssuerLocation("https://appleid.apple.com")
                .registrationId("apple")
                .clientId(appleClientId.trim())
                .clientSecret(appleClientSecret.trim())
                .clientName("Apple")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "name")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .build());
        }
        return new OptionalClientRegistrationRepository(registrations);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static final class OptionalClientRegistrationRepository
        implements ClientRegistrationRepository, Iterable<ClientRegistration> {

        private final Map<String, ClientRegistration> registrations;

        OptionalClientRegistrationRepository(Map<String, ClientRegistration> registrations) {
            this.registrations = Map.copyOf(registrations);
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return registrations.get(registrationId);
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return List.copyOf(registrations.values()).iterator();
        }
    }
}
