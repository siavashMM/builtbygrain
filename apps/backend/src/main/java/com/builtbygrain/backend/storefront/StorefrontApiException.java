package com.builtbygrain.backend.storefront;

import org.springframework.http.HttpStatus;

public final class StorefrontApiException extends RuntimeException {
    private final HttpStatus status;

    StorefrontApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
