package com.builtbygrain.backend.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findAllByCustomerIdOrderByCreatedAtAsc(Long customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);
}
