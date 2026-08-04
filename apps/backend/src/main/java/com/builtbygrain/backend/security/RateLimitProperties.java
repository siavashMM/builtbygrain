package com.builtbygrain.backend.security;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    @Valid private final Limit customerLoginAccount = new Limit(5, Duration.ofMinutes(15));
    @Valid private final Limit customerLoginIp = new Limit(20, Duration.ofMinutes(15));
    @Valid private final Limit adminLoginAccount = new Limit(5, Duration.ofMinutes(15));
    @Valid private final Limit adminLoginIp = new Limit(10, Duration.ofMinutes(15));
    @Valid private final Limit registrationIp = new Limit(5, Duration.ofHours(1));
    @Valid private final Limit passwordResetRequestAccount = new Limit(3, Duration.ofHours(1));
    @Valid private final Limit passwordResetRequestIp = new Limit(10, Duration.ofHours(1));
    @Valid private final Limit passwordResetToken = new Limit(5, Duration.ofMinutes(15));
    @Valid private final Limit passwordResetIp = new Limit(10, Duration.ofMinutes(15));
    @Valid private final Limit adminWrite = new Limit(30, Duration.ofMinutes(1));
    @Valid private final Limit adminUpload = new Limit(10, Duration.ofMinutes(10));
    @Valid private final Limit customerWrite = new Limit(30, Duration.ofMinutes(1));

    public Limit getCustomerLoginAccount() { return customerLoginAccount; }
    public Limit getCustomerLoginIp() { return customerLoginIp; }
    public Limit getAdminLoginAccount() { return adminLoginAccount; }
    public Limit getAdminLoginIp() { return adminLoginIp; }
    public Limit getRegistrationIp() { return registrationIp; }
    public Limit getPasswordResetRequestAccount() { return passwordResetRequestAccount; }
    public Limit getPasswordResetRequestIp() { return passwordResetRequestIp; }
    public Limit getPasswordResetToken() { return passwordResetToken; }
    public Limit getPasswordResetIp() { return passwordResetIp; }
    public Limit getAdminWrite() { return adminWrite; }
    public Limit getAdminUpload() { return adminUpload; }
    public Limit getCustomerWrite() { return customerWrite; }

    public static class Limit {
        @Min(1)
        private int attempts;

        @NotNull
        private Duration window;

        public Limit() { }

        public Limit(int attempts, Duration window) {
            this.attempts = attempts;
            this.window = window;
        }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }

        @AssertTrue(message = "rate-limit windows must be at least one second")
        public boolean isWindowValid() {
            return window == null || window.compareTo(Duration.ofSeconds(1)) >= 0;
        }
    }
}
