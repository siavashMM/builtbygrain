package com.builtbygrain.backend.security;

import com.builtbygrain.backend.admin.AdminAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccounts;
    private final String regularUsername;
    private final String regularPasswordHash;

    public DatabaseUserDetailsService(
        AdminAccountRepository adminAccounts,
        PasswordEncoder passwordEncoder,
        @Value("${app.security.user.username:user}") String userUsername,
        @Value("${app.security.user.password:password}") String userPassword
    ) {
        this.adminAccounts = adminAccounts;
        this.regularUsername = userUsername;
        this.regularPasswordHash = passwordEncoder.encode(userPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return adminAccounts.findByUsernameIgnoreCase(username)
            .<UserDetails>map(account -> User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles("ADMIN")
                .disabled(!account.isEnabled())
                .build())
            .orElseGet(() -> loadRegularUser(username));
    }

    private UserDetails loadRegularUser(String username) {
        if (regularUsername.equals(username)) {
            return User.withUsername(regularUsername)
                .password(regularPasswordHash)
                .roles("USER")
                .build();
        }
        throw new UsernameNotFoundException("User not found");
    }
}
