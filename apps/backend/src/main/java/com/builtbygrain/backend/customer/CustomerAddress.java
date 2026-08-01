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

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(length = 200)
    private String company;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(name = "house_number", nullable = false, length = 30)
    private String houseNumber;

    @Column(name = "address_line_2", length = 200)
    private String addressLine2;

    @Column(name = "postal_code", nullable = false, length = 30)
    private String postalCode;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 40)
    private String phone;

    @Column(name = "default_shipping", nullable = false)
    private boolean defaultShipping;

    @Column(name = "default_billing", nullable = false)
    private boolean defaultBilling;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerAddress() {
    }

    public CustomerAddress(Customer customer) {
        this.customer = customer;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getCompany() {
        return company;
    }

    public String getStreet() {
        return street;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isDefaultShipping() {
        return defaultShipping;
    }

    public boolean isDefaultBilling() {
        return defaultBilling;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(
        String recipientName,
        String company,
        String street,
        String houseNumber,
        String addressLine2,
        String postalCode,
        String city,
        String region,
        String countryCode,
        String phone
    ) {
        this.recipientName = recipientName;
        this.company = company;
        this.street = street;
        this.houseNumber = houseNumber;
        this.addressLine2 = addressLine2;
        this.postalCode = postalCode;
        this.city = city;
        this.region = region;
        this.countryCode = countryCode;
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDefaultShipping(boolean defaultShipping) {
        this.defaultShipping = defaultShipping;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDefaultBilling(boolean defaultBilling) {
        this.defaultBilling = defaultBilling;
        this.updatedAt = LocalDateTime.now();
    }
}
