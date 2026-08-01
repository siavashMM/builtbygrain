package com.builtbygrain.backend.customer;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "customer_social_identities",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_customer_social_provider_subject", columnNames = {"provider", "provider_subject"}),
        @UniqueConstraint(name = "uq_customer_social_provider_customer", columnNames = {"provider", "customer_id"})
    }
)
public class CustomerSocialIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CustomerSocialIdentity() {
    }

    public CustomerSocialIdentity(Customer customer, String provider, String providerSubject) {
        this.customer = customer;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = LocalDateTime.now();
    }

    public Customer getCustomer() {
        return customer;
    }
}
