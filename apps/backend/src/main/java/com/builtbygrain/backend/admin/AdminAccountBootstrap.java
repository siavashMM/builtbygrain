package com.builtbygrain.backend.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private final AdminAccountRepository adminAccounts;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountBootstrap(
        AdminAccountRepository adminAccounts,
        PasswordEncoder passwordEncoder,
        @Value("${app.security.admin.username:}") String username,
        @Value("${app.security.admin.password:}") String password
    ) {
        this.adminAccounts = adminAccounts;
        this.passwordEncoder = passwordEncoder;
        this.username = username.trim();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }

        adminAccounts.findByUsernameIgnoreCase(username)
            .orElseGet(() -> adminAccounts.save(
                new AdminAccount(username, passwordEncoder.encode(password))
            ));
    }
}

