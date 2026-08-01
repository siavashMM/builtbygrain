package com.builtbygrain.backend.security;

import java.util.Arrays;
import java.util.List;

import com.builtbygrain.backend.customer.AppleAuthorizationRequestResolver;
import com.builtbygrain.backend.customer.CustomerSocialAuthenticationFailureHandler;
import com.builtbygrain.backend.customer.CustomerSocialAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        AuthenticationEntryPoint apiAuthenticationEntryPoint,
        SecurityContextRepository securityContextRepository,
        CsrfTokenRepository csrfTokenRepository,
        ClientRegistrationRepository clientRegistrations,
        CustomerSocialAuthenticationSuccessHandler socialSuccessHandler,
        CustomerSocialAuthenticationFailureHandler socialFailureHandler
    ) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers(request ->
                    "POST".equals(request.getMethod()) && "/login/oauth2/code/apple".equals(request.getRequestURI()))
            )
            .cors(cors -> { })
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/navigation/**").permitAll()
                .requestMatchers("/api/admin/auth/csrf", "/api/admin/auth/login").permitAll()
                .requestMatchers(
                    "/api/account/auth/csrf",
                    "/api/account/auth/register",
                    "/api/account/auth/login",
                    "/api/account/auth/social/**",
                    "/api/account/auth/forgot-password",
                    "/api/account/auth/reset-password"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/account/**").hasRole("CUSTOMER")
                .anyRequest().denyAll()
            )
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true)
            )
            .requestCache(cache -> cache.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(apiAuthenticationEntryPoint)
            )
            .formLogin(login -> login.disable())
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(new AppleAuthorizationRequestResolver(clientRegistrations)))
                .successHandler(socialSuccessHandler)
                .failureHandler(socialFailureHandler)
            )
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(
        @Value("${server.servlet.session.cookie.secure:false}") boolean secure
    ) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CookieSerializer adminSessionCookie(
        @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
        @Value("${app.security.session-cookie-same-site:Lax}") String sameSite
    ) {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName("BBG_ADMIN_SESSION");
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(secure);
        cookie.setSameSite(sameSite);
        cookie.setCookieMaxAge(Math.toIntExact(java.time.Duration.ofDays(30).toSeconds()));
        return cookie;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${app.security.cors.allowed-origins:http://localhost:4200}") String configuredOrigins
    ) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
