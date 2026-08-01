package com.builtbygrain.backend.customer;

import java.util.List;
import java.util.Locale;

import com.builtbygrain.backend.customer.CustomerDtos.AddressRequest;
import com.builtbygrain.backend.customer.CustomerDtos.AddressResponse;
import com.builtbygrain.backend.customer.CustomerDtos.CustomerResponse;
import com.builtbygrain.backend.customer.CustomerDtos.ProfileUpdateRequest;
import com.builtbygrain.backend.customer.CustomerDtos.RegisterRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerAccountService {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;
    private final PasswordEncoder passwordEncoder;

    public CustomerAccountService(
        CustomerRepository customers,
        CustomerAddressRepository addresses,
        PasswordEncoder passwordEncoder
    ) {
        this.customers = customers;
        this.addresses = addresses;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Customer register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (customers.existsByNormalizedEmail(normalizedEmail)) {
            throw conflict("An account already exists for this email address.");
        }

        Customer customer = new Customer(
            request.email().trim(),
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            required(request.firstName()),
            required(request.lastName()),
            optional(request.locale()) == null ? "en" : request.locale().trim()
        );
        try {
            return customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("An account already exists for this email address.");
        }
    }

    @Transactional(readOnly = true)
    public Customer requireCustomer(String principalName) {
        return customers.findByNormalizedEmail(normalizeEmail(principalName))
            .filter(Customer::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    @Transactional(readOnly = true)
    public CustomerResponse profile(String principalName) {
        return CustomerResponse.from(requireCustomer(principalName));
    }

    @Transactional
    public CustomerResponse updateProfile(String principalName, ProfileUpdateRequest request) {
        Customer customer = requireCustomer(principalName);
        String normalizedEmail = normalizeEmail(request.email());
        customers.findByNormalizedEmail(normalizedEmail)
            .filter(other -> !other.getId().equals(customer.getId()))
            .ifPresent(other -> {
                throw conflict("An account already exists for this email address.");
            });

        customer.updateProfile(
            request.email().trim(),
            normalizedEmail,
            required(request.firstName()),
            required(request.lastName()),
            optional(request.phone()),
            required(request.locale())
        );
        try {
            customers.flush();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("An account already exists for this email address.");
        }
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(String principalName) {
        Customer customer = requireCustomer(principalName);
        return addresses.findAllByCustomerIdOrderByCreatedAtAsc(customer.getId()).stream()
            .map(AddressResponse::from)
            .toList();
    }

    @Transactional
    public AddressResponse addAddress(String principalName, AddressRequest request) {
        Customer customer = requireCustomer(principalName);
        List<CustomerAddress> existing = addresses.findAllByCustomerIdOrderByCreatedAtAsc(customer.getId());
        CustomerAddress address = new CustomerAddress(customer);
        updateAddressFields(address, request);
        applyDefaults(existing, address, request.defaultShipping() || existing.isEmpty(),
            request.defaultBilling() || existing.isEmpty());
        return AddressResponse.from(addresses.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(String principalName, Long addressId, AddressRequest request) {
        Customer customer = requireCustomer(principalName);
        CustomerAddress address = requireOwnedAddress(customer.getId(), addressId);
        List<CustomerAddress> existing = addresses.findAllByCustomerIdOrderByCreatedAtAsc(customer.getId());
        updateAddressFields(address, request);
        applyDefaults(existing, address, request.defaultShipping(), request.defaultBilling());
        if (!request.defaultShipping()) {
            address.setDefaultShipping(false);
        }
        if (!request.defaultBilling()) {
            address.setDefaultBilling(false);
        }
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse chooseDefault(String principalName, Long addressId, boolean shipping) {
        Customer customer = requireCustomer(principalName);
        CustomerAddress selected = requireOwnedAddress(customer.getId(), addressId);
        List<CustomerAddress> existing = addresses.findAllByCustomerIdOrderByCreatedAtAsc(customer.getId());
        if (shipping) {
            existing.forEach(address -> address.setDefaultShipping(address.getId().equals(selected.getId())));
        } else {
            existing.forEach(address -> address.setDefaultBilling(address.getId().equals(selected.getId())));
        }
        return AddressResponse.from(selected);
    }

    @Transactional
    public void deleteAddress(String principalName, Long addressId) {
        Customer customer = requireCustomer(principalName);
        CustomerAddress address = requireOwnedAddress(customer.getId(), addressId);
        boolean replaceShipping = address.isDefaultShipping();
        boolean replaceBilling = address.isDefaultBilling();
        addresses.delete(address);
        addresses.flush();
        addresses.findAllByCustomerIdOrderByCreatedAtAsc(customer.getId()).stream().findFirst()
            .ifPresent(first -> {
                if (replaceShipping) first.setDefaultShipping(true);
                if (replaceBilling) first.setDefaultBilling(true);
            });
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private CustomerAddress requireOwnedAddress(Long customerId, Long addressId) {
        return addresses.findByIdAndCustomerId(addressId, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void updateAddressFields(CustomerAddress address, AddressRequest request) {
        address.update(
            required(request.recipientName()),
            optional(request.company()),
            required(request.street()),
            required(request.houseNumber()),
            optional(request.addressLine2()),
            required(request.postalCode()),
            required(request.city()),
            optional(request.region()),
            request.countryCode().trim().toUpperCase(Locale.ROOT),
            optional(request.phone())
        );
    }

    private void applyDefaults(
        List<CustomerAddress> existing,
        CustomerAddress selected,
        boolean defaultShipping,
        boolean defaultBilling
    ) {
        if (defaultShipping) {
            existing.forEach(address -> address.setDefaultShipping(false));
            selected.setDefaultShipping(true);
        }
        if (defaultBilling) {
            existing.forEach(address -> address.setDefaultBilling(false));
            selected.setDefaultBilling(true);
        }
    }

    private static String required(String value) {
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
