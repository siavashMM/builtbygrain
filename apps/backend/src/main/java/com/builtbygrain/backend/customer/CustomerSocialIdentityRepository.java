package com.builtbygrain.backend.customer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSocialIdentityRepository extends JpaRepository<CustomerSocialIdentity, Long> {

    Optional<CustomerSocialIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
