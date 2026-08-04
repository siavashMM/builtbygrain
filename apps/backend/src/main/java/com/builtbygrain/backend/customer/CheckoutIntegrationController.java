package com.builtbygrain.backend.customer;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutIntegrationController {

    static final String SOCIAL_RETURN_URL = "checkout.social.return-url";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "apple");

    private final ClientRegistrationRepository registrations;
    private final String googleMapsBrowserKey;

    public CheckoutIntegrationController(
        ClientRegistrationRepository registrations,
        @Value("${app.checkout.google-maps-browser-key:}") String googleMapsBrowserKey
    ) {
        this.registrations = registrations;
        this.googleMapsBrowserKey = googleMapsBrowserKey == null ? "" : googleMapsBrowserKey.trim();
    }

    @GetMapping("/api/public/checkout/config")
    public CheckoutConfig config() {
        return new CheckoutConfig(
            Map.of(
                "google", registrations.findByRegistrationId("google") != null,
                "apple", registrations.findByRegistrationId("apple") != null
            ),
            !googleMapsBrowserKey.isBlank(),
            googleMapsBrowserKey.isBlank() ? null : googleMapsBrowserKey
        );
    }

    @GetMapping("/api/account/auth/social/{provider}")
    public ResponseEntity<Void> socialLogin(
        @PathVariable String provider,
        String returnUrl,
        HttpServletRequest request
    ) {
        String normalized = provider.toLowerCase(java.util.Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(normalized) || registrations.findByRegistrationId(normalized) == null) {
            return ResponseEntity.notFound().build();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SOCIAL_RETURN_URL, CustomerAuthController.safeReturnUrl(returnUrl));
        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, URI.create("/oauth2/authorization/" + normalized).toString())
            .build();
    }

    static String socialFailureUrl(Object requestedReturnUrl) {
        String returnUrl = CustomerAuthController.safeReturnUrl(
            requestedReturnUrl instanceof String value ? value : "/account"
        );
        String source = returnUrl.startsWith("/checkout/") ? "/checkout/account" : "/account/sign-in";
        return source + "?socialError=failed&returnUrl="
            + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
    }

    public record CheckoutConfig(
        Map<String, Boolean> socialProviders,
        boolean addressAutocompleteEnabled,
        String googleMapsBrowserKey
    ) {
    }
}
