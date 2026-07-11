package com.builtbygrain.backend.admin;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByUsernameIgnoreCase(String username);
}

