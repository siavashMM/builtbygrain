package com.builtbygrain.backend.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPasswordService {

    private final AdminAccountRepository adminAccounts;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public AdminPasswordService(
        AdminAccountRepository adminAccounts,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbc
    ) {
        this.adminAccounts = adminAccounts;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminAccount account = adminAccounts.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "New password must be different from the current password"
            );
        }

        account.changePasswordHash(passwordEncoder.encode(newPassword));
        adminAccounts.save(account);

        // The Flyway-managed Spring Session foreign key cascades attribute deletion.
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", account.getUsername());
    }
}
