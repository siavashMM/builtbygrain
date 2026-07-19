package com.builtbygrain.backend.storefront;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.builtbygrain.backend.product.ProductImageStorageException;

@RestControllerAdvice(basePackages = "com.builtbygrain.backend.storefront")
public class StorefrontErrorHandler {
    @ExceptionHandler(StorefrontApiException.class)
    ResponseEntity<Map<String, Object>> storefrontError(StorefrontApiException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
            "status", exception.status().value(),
            "error", exception.status().getReasonPhrase(),
            "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of(
            "status", 400,
            "error", "Bad Request",
            "message", message
        ));
    }

    @ExceptionHandler(ProductImageStorageException.class)
    ResponseEntity<Map<String, Object>> imageError(ProductImageStorageException exception) {
        return ResponseEntity.badRequest().body(Map.of(
            "status", 400,
            "error", "Bad Request",
            "message", exception.getMessage()
        ));
    }
}
