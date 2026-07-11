package com.builtbygrain.backend.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ProductSlugAlreadyExistsException extends RuntimeException {

    public ProductSlugAlreadyExistsException(String slug) {
        super("Product slug already exists: " + slug);
    }
}
