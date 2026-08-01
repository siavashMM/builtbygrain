package com.builtbygrain.backend.customer;

import java.security.Principal;
import java.util.List;

import com.builtbygrain.backend.customer.CustomerDtos.AddressRequest;
import com.builtbygrain.backend.customer.CustomerDtos.AddressResponse;
import com.builtbygrain.backend.customer.CustomerDtos.CustomerResponse;
import com.builtbygrain.backend.customer.CustomerDtos.ProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class CustomerAccountController {

    private final CustomerAccountService accounts;
    private final CustomerSessionService sessions;

    public CustomerAccountController(CustomerAccountService accounts, CustomerSessionService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @GetMapping("/profile")
    public CustomerResponse profile(Principal principal) {
        return accounts.profile(principal.getName());
    }

    @PutMapping("/profile")
    public CustomerResponse updateProfile(
        @Valid @RequestBody ProfileUpdateRequest update,
        Principal principal,
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response
    ) {
        String previousEmail = principal.getName();
        CustomerResponse updated = accounts.updateProfile(previousEmail, update);
        if (!CustomerAccountService.normalizeEmail(updated.email()).equals(previousEmail)) {
            sessions.invalidateAll(previousEmail);
            sessions.authenticate(accounts.requireCustomer(updated.email()), request, response);
        }
        return updated;
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses(Principal principal) {
        return accounts.addresses(principal.getName());
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(
        @Valid @RequestBody AddressRequest address,
        Principal principal
    ) {
        return ResponseEntity.status(201).body(accounts.addAddress(principal.getName(), address));
    }

    @PutMapping("/addresses/{addressId}")
    public AddressResponse updateAddress(
        @PathVariable Long addressId,
        @Valid @RequestBody AddressRequest address,
        Principal principal
    ) {
        return accounts.updateAddress(principal.getName(), addressId, address);
    }

    @PostMapping("/addresses/{addressId}/default-shipping")
    public AddressResponse defaultShipping(@PathVariable Long addressId, Principal principal) {
        return accounts.chooseDefault(principal.getName(), addressId, true);
    }

    @PostMapping("/addresses/{addressId}/default-billing")
    public AddressResponse defaultBilling(@PathVariable Long addressId, Principal principal) {
        return accounts.chooseDefault(principal.getName(), addressId, false);
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId, Principal principal) {
        accounts.deleteAddress(principal.getName(), addressId);
        return ResponseEntity.noContent().build();
    }
}
