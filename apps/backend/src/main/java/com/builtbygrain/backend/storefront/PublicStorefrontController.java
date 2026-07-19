package com.builtbygrain.backend.storefront;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.builtbygrain.backend.storefront.StorefrontDtos.PublicStorefrontDto;

@RestController
public class PublicStorefrontController {
    private final StorefrontConfigurationService storefront;

    public PublicStorefrontController(StorefrontConfigurationService storefront) {
        this.storefront = storefront;
    }

    @GetMapping("/api/public/storefront")
    public PublicStorefrontDto storefront() { return storefront.publicStorefront(); }
}
