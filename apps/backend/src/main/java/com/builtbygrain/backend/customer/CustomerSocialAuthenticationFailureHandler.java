package com.builtbygrain.backend.customer;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomerSocialAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        Object requestedReturnUrl = session == null
            ? null
            : session.getAttribute(CheckoutIntegrationController.SOCIAL_RETURN_URL);
        response.sendRedirect(CheckoutIntegrationController.socialFailureUrl(requestedReturnUrl));
    }
}
