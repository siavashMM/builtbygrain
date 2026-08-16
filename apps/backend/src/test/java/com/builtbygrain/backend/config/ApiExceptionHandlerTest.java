package com.builtbygrain.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.builtbygrain.backend.customer.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ApiExceptionHandlerTest {

    @Test
    void optimisticCustomerConflictReturnsControlledGenericResponse() {
        ResponseEntity<Map<String, Object>> response = new ApiExceptionHandler().optimisticLockConflict(
            new ObjectOptimisticLockingFailureException(Customer.class, 42L)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409)
            .containsEntry("error", "Conflict")
            .containsEntry("message", "The account was updated by another request. Please retry.");
    }
}
