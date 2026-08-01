package com.builtbygrain.backend.admin;

import java.security.Principal;
import java.time.Duration;

import com.builtbygrain.backend.security.LoginThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private static final Duration THROTTLE_DURATION = Duration.ofMinutes(15);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContexts;
    private final LoginThrottleService throttle;
    private final CsrfTokenRepository csrfTokens;
    private final AdminPasswordService passwords;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AdminAuthController(
        AuthenticationManager authenticationManager,
        SecurityContextRepository securityContexts,
        LoginThrottleService throttle,
        CsrfTokenRepository csrfTokens,
        AdminPasswordService passwords
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContexts = securityContexts;
        this.throttle = throttle;
        this.csrfTokens = csrfTokens;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokens.generateToken(request);
        csrfTokens.saveToken(token, request, response);
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
        @Valid @RequestBody AdminLoginRequest credentials,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String address = request.getRemoteAddr();
        if (throttle.isBlocked(credentials.username(), address)) {
            return throttled();
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    credentials.username().trim(),
                    credentials.password()
                )
            );
            throttle.recordSuccess(authentication.getName(), address);

            HttpSession previousSession = request.getSession(false);
            if (previousSession != null) {
                previousSession.invalidate();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true);
            securityContexts.saveContext(context, request, response);

            return ResponseEntity.ok(new AdminLoginResponse(authentication.getName(), true));
        } catch (AuthenticationException exception) {
            if (throttle.recordFailure(credentials.username(), address)) {
                return throttled();
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthError("Unauthorized"));
        }
    }

    @GetMapping("/session")
    public AdminLoginResponse session(Principal principal) {
        return new AdminLoginResponse(principal.getName(), true);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody AdminPasswordChangeRequest passwordChange,
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        passwords.changePassword(
            authentication.getName(),
            passwordChange.currentPassword(),
            passwordChange.newPassword()
        );
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthError> throttled() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(THROTTLE_DURATION.toSeconds()))
            .body(new AuthError("Too many login attempts"));
    }

    public record AdminLoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 1000) String password
    ) {
    }

    public record AdminLoginResponse(String username, boolean admin) {
    }

    public record AdminPasswordChangeRequest(
        @NotBlank @Size(max = 1000) String currentPassword,
        @NotBlank @Size(min = 12, max = 1000) String newPassword
    ) {
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record AuthError(String error) {
    }
}
