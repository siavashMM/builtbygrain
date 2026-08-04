package com.builtbygrain.backend.customer;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 20) String locale,
        @Size(max = 2048) String returnUrl
    ) {
        public RegisterRequest {
            if (email != null) email = email.trim();
        }
    }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 2048) String returnUrl
    ) {
        public LoginRequest {
            if (email != null) email = email.trim();
        }
    }

    public record AuthResponse(CustomerResponse customer, String returnUrl) {
    }

    public record CustomerResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String locale,
        boolean emailVerified,
        boolean passwordSet,
        String accountStatus,
        LocalDateTime createdAt
    ) {
        static CustomerResponse from(Customer customer) {
            return new CustomerResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPhone(),
                customer.getLocale(),
                customer.getEmailVerifiedAt() != null,
                customer.getPasswordHash() != null,
                customer.getAccountStatus().name(),
                customer.getCreatedAt()
            );
        }
    }

    public record ProfileUpdateRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 40) String phone,
        @NotBlank @Size(max = 20) String locale
    ) {
        public ProfileUpdateRequest {
            if (email != null) email = email.trim();
        }
    }

    public record PasswordChangeRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {
        public ForgotPasswordRequest {
            if (email != null) email = email.trim();
        }
    }

    public record ResetPasswordRequest(
        @NotBlank @Size(min = 32, max = 256) String token,
        @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {
    }

    public record MessageResponse(String message) {
    }

    public record AddressRequest(
        @NotBlank @Size(max = 200) String recipientName,
        @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Size(max = 30) String houseNumber,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 30) String postalCode,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 120) String region,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @Size(max = 40) String phone,
        boolean defaultShipping,
        boolean defaultBilling
    ) {
    }

    public record AddressResponse(
        Long id,
        String recipientName,
        String company,
        String street,
        String houseNumber,
        String addressLine2,
        String postalCode,
        String city,
        String region,
        String countryCode,
        String phone,
        boolean defaultShipping,
        boolean defaultBilling,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        static AddressResponse from(CustomerAddress address) {
            return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getCompany(),
                address.getStreet(),
                address.getHouseNumber(),
                address.getAddressLine2(),
                address.getPostalCode(),
                address.getCity(),
                address.getRegion(),
                address.getCountryCode(),
                address.getPhone(),
                address.isDefaultShipping(),
                address.isDefaultBilling(),
                address.getCreatedAt(),
                address.getUpdatedAt()
            );
        }
    }
}
