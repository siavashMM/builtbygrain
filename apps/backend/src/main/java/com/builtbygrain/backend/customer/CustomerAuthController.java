package com.builtbygrain.backend.customer;

import java.security.Principal;

import com.builtbygrain.backend.customer.CustomerAuthThrottleService.Action;
import com.builtbygrain.backend.customer.CustomerDtos.AuthResponse;
import com.builtbygrain.backend.customer.CustomerDtos.CustomerResponse;
import com.builtbygrain.backend.customer.CustomerDtos.ForgotPasswordRequest;
import com.builtbygrain.backend.customer.CustomerDtos.LoginRequest;
import com.builtbygrain.backend.customer.CustomerDtos.MessageResponse;
import com.builtbygrain.backend.customer.CustomerDtos.PasswordChangeRequest;
import com.builtbygrain.backend.customer.CustomerDtos.RegisterRequest;
import com.builtbygrain.backend.customer.CustomerDtos.ResetPasswordRequest;
import com.builtbygrain.backend.security.LoginThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/auth")
public class CustomerAuthController {

    private static final long RETRY_AFTER_SECONDS = 900;
    private static final MessageResponse FORGOT_RESPONSE = new MessageResponse(
        "If an account exists for that email address, a reset link has been sent."
    );

    private final AuthenticationManager authenticationManager;
    private final CustomerAccountService accounts;
    private final CustomerPasswordService passwords;
    private final CustomerSessionService sessions;
    private final LoginThrottleService loginThrottle;
    private final CustomerAuthThrottleService requestThrottle;
    private final CsrfTokenRepository csrfTokens;

    public CustomerAuthController(
        AuthenticationManager authenticationManager,
        CustomerAccountService accounts,
        CustomerPasswordService passwords,
        CustomerSessionService sessions,
        LoginThrottleService loginThrottle,
        CustomerAuthThrottleService requestThrottle,
        CsrfTokenRepository csrfTokens
    ) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessions = sessions;
        this.loginThrottle = loginThrottle;
        this.requestThrottle = requestThrottle;
        this.csrfTokens = csrfTokens;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokens.generateToken(request);
        csrfTokens.saveToken(token, request, response);
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
        @Valid @RequestBody RegisterRequest registration,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (!requestThrottle.consume(Action.REGISTER, request.getRemoteAddr())) {
            return throttled();
        }
        Customer customer = accounts.register(registration);
        sessions.authenticate(customer, request, response);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AuthResponse(CustomerResponse.from(customer), safeReturnUrl(registration.returnUrl())));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
        @Valid @RequestBody LoginRequest credentials,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String normalizedEmail = CustomerAccountService.normalizeEmail(credentials.email());
        String address = request.getRemoteAddr();
        if (loginThrottle.isBlocked(normalizedEmail, address)) {
            return throttled();
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, credentials.password())
            );
            if (authentication.getAuthorities().stream().noneMatch(authority ->
                "ROLE_CUSTOMER".equals(authority.getAuthority()))) {
                throw new org.springframework.security.authentication.BadCredentialsException("Wrong account type");
            }
            loginThrottle.recordSuccess(normalizedEmail, address);
            Customer customer = accounts.requireCustomer(authentication.getName());
            sessions.authenticate(customer, request, response);
            return ResponseEntity.ok(
                new AuthResponse(CustomerResponse.from(customer), safeReturnUrl(credentials.returnUrl()))
            );
        } catch (AuthenticationException exception) {
            if (loginThrottle.recordFailure(normalizedEmail, address)) {
                return throttled();
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthError("Email or password is incorrect"));
        }
    }

    @GetMapping("/session")
    public CustomerResponse session(Principal principal) {
        return accounts.profile(principal.getName());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        sessions.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest forgot,
        HttpServletRequest request
    ) {
        if (!requestThrottle.consume(Action.PASSWORD_RESET_REQUEST, request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER_SECONDS))
                .body(FORGOT_RESPONSE);
        }
        passwords.requestReset(forgot.email());
        return ResponseEntity.accepted().body(FORGOT_RESPONSE);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
        @Valid @RequestBody ResetPasswordRequest reset,
        HttpServletRequest request
    ) {
        if (!requestThrottle.consume(Action.PASSWORD_RESET, request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER_SECONDS))
                .body(new MessageResponse("Too many attempts. Please try again later."));
        }
        passwords.resetPassword(reset.token(), reset.newPassword());
        return ResponseEntity.ok(new MessageResponse("Your password has been reset. You can now sign in."));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody PasswordChangeRequest change,
        Principal principal,
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        passwords.changePassword(accounts.requireCustomer(principal.getName()), change.currentPassword(), change.newPassword());
        sessions.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    static String safeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "/account";
        }
        String candidate = returnUrl.trim();
        if (!candidate.startsWith("/")
            || candidate.startsWith("//")
            || candidate.contains("\\")
            || candidate.chars().anyMatch(Character::isISOControl)) {
            return "/account";
        }
        return candidate;
    }

    private ResponseEntity<AuthError> throttled() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER_SECONDS))
            .body(new AuthError("Too many attempts. Please try again later."));
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record AuthError(String error) {
    }
}
