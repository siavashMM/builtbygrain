package com.builtbygrain.backend.customer;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
        update PasswordResetToken token set token.usedAt = :usedAt
        where token.customer.id = :customerId and token.usedAt is null
        """)
    int invalidateUnusedForCustomer(Long customerId, LocalDateTime usedAt);
}
