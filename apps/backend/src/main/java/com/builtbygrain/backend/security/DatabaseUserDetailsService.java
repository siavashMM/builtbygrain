package com.builtbygrain.backend.security;

import java.util.UUID;

import com.builtbygrain.backend.admin.AdminAccountRepository;
import com.builtbygrain.backend.customer.CustomerRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccounts;
    private final CustomerRepository customers;

    public DatabaseUserDetailsService(
        AdminAccountRepository adminAccounts,
        CustomerRepository customers
    ) {
        this.adminAccounts = adminAccounts;
        this.customers = customers;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return adminAccounts.findByUsernameIgnoreCase(username)
            .<UserDetails>map(account -> User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles("ADMIN")
                .disabled(!account.isEnabled())
                .build())
            .orElseGet(() -> loadCustomer(username));
    }

    private UserDetails loadCustomer(String username) {
        return customers.findByNormalizedEmail(username.trim().toLowerCase(java.util.Locale.ROOT))
            .<UserDetails>map(customer -> User.withUsername(customer.getNormalizedEmail())
                // Social-only accounts have no reusable local password. A fresh,
                // unguessable value also keeps password authentication fail-closed.
                .password(customer.getPasswordHash() == null ? "{noop}" + UUID.randomUUID() : customer.getPasswordHash())
                .roles("CUSTOMER")
                .disabled(!customer.isActive())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
