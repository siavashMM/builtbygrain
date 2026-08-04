package com.builtbygrain.backend.customer;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomerSocialAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final CustomerSocialAccountService socialAccounts;
    private final CustomerSessionService sessions;

    public CustomerSocialAuthenticationSuccessHandler(
        CustomerSocialAccountService socialAccounts,
        CustomerSessionService sessions
    ) {
        this.socialAccounts = socialAccounts;
        this.sessions = sessions;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
            || !(token.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendRedirect("/checkout/account?socialError=unsupported");
            return;
        }

        HttpSession currentSession = request.getSession(false);
        Object requestedReturnUrl = currentSession == null
            ? null
            : currentSession.getAttribute(CheckoutIntegrationController.SOCIAL_RETURN_URL);
        try {
            Customer customer = socialAccounts.provision(token.getAuthorizedClientRegistrationId(), oidcUser);
            sessions.authenticate(customer, request, response);
            response.sendRedirect(CustomerAuthController.safeReturnUrl(
                requestedReturnUrl instanceof String value ? value : "/checkout/delivery"
            ));
        } catch (RuntimeException exception) {
            response.sendRedirect(CheckoutIntegrationController.socialFailureUrl(requestedReturnUrl));
        }
    }
}
