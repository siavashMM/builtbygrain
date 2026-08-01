package com.builtbygrain.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.builtbygrain.backend.product.ProductImageStorageException;
import com.builtbygrain.backend.product.ProductNotFoundException;
import com.builtbygrain.backend.product.ProductSlugAlreadyExistsException;
import com.builtbygrain.backend.storefront.StorefrontApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(StorefrontApiException.class)
    ResponseEntity<Map<String, Object>> storefront(StorefrontApiException exception) {
        return response(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<Map<String, Object>> productNotFound(ProductNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ProductSlugAlreadyExistsException.class)
    ResponseEntity<Map<String, Object>> productConflict(ProductSlugAlreadyExistsException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ProductImageStorageException.class)
    ResponseEntity<Map<String, Object>> imageValidation(ProductImageStorageException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, exception.getReason() == null ? status.getReasonPhrase() : exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> bodyValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElseGet(() -> exception.getBindingResult().getGlobalErrors().stream().findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Request validation failed" : error.getDefaultMessage())
                .orElse("Request validation failed"));
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
        MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, Object>> requestValidation(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integrityConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing catalog data.");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<Map<String, Object>> routeNotFound(Exception exception) {
        return response(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
        String errorRef = UUID.randomUUID().toString();
        LOGGER.error("Unexpected API error {}", errorRef, exception);
        Map<String, Object> body = body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        body.put("errorRef", errorRef);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(body(status, message));
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status.value());
        result.put("error", status.getReasonPhrase());
        result.put("message", message);
        return result;
    }
}
