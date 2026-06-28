package com.builtbygrain.backend.shop;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicProductsController {

    @GetMapping("/api/public/products/test")
    public TestResponse testProduct() {
        return new TestResponse("public product endpoint");
    }
}
