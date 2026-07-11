package com.builtbygrain.backend.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProductImageStorageException extends RuntimeException {

    public ProductImageStorageException(String message) {
        super(message);
    }
}
