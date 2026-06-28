package com.builtbygrain.backend.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.builtbygrain.backend.shop.TestResponse;

@RestController
public class AdminProductsController {

    @GetMapping("/api/admin/products/test")
    public TestResponse testAdminProduct() {
        return new TestResponse("admin product endpoint");
    }
}
