package com.builtbygrain.backend.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetPageUrl;

    public PasswordResetMailService(
        JavaMailSender mailSender,
        @Value("${app.customer-auth.mail.from:no-reply@builtbygrain.local}") String from,
        @Value("${app.customer-auth.password-reset-url:http://localhost:4200/account/reset-password}") String resetPageUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetPageUrl = resetPageUrl;
    }

    public void send(Customer customer, String rawToken) {
        String link = UriComponentsBuilder.fromUriString(resetPageUrl)
            .queryParam("token", rawToken)
            .build()
            .encode()
            .toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(customer.getEmail());
        message.setSubject("Reset your Built by Grain password");
        message.setText("""
            Hello %s,

            Use the link below within 30 minutes to choose a new password:

            %s

            If you did not request this, you can ignore this message.
            """.formatted(customer.getFirstName(), link));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // Password-reset tokens are deliberately never written to logs.
            LOGGER.warn("Password reset email delivery failed for customer id {}", customer.getId());
        }
    }
}
