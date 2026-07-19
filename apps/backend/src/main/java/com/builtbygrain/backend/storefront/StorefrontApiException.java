package com.builtbygrain.backend.storefront;

import org.springframework.http.HttpStatus;

final class StorefrontApiException extends RuntimeException {
    private final HttpStatus status;

    StorefrontApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() { return status; }
}
