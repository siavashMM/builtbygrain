package com.builtbygrain.backend.customer;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerSessionService {

    private final SecurityContextRepository securityContexts;
    private final JdbcTemplate jdbc;
    private final int customerSessionSeconds;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public CustomerSessionService(
        SecurityContextRepository securityContexts,
        JdbcTemplate jdbc,
        @Value("${app.customer-auth.session-lifetime:30d}") Duration customerSessionLifetime
    ) {
        this.securityContexts = securityContexts;
        this.jdbc = jdbc;
        this.customerSessionSeconds = Math.toIntExact(customerSessionLifetime.toSeconds());
    }

    public void authenticate(Customer customer, HttpServletRequest request, HttpServletResponse response) {
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            customer.getNormalizedEmail(),
            null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(customerSessionSeconds);
        securityContexts.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
    }

    public void invalidateAll(String normalizedEmail) {
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", normalizedEmail);
    }
}
