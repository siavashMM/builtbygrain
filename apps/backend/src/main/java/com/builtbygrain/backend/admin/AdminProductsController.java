package com.builtbygrain.backend.admin;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.builtbygrain.backend.product.ProductRequest;
import com.builtbygrain.backend.product.ProductResponse;
import com.builtbygrain.backend.product.ProductService;

@RestController
public class AdminProductsController {

    private final ProductService productService;

    public AdminProductsController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/api/admin/products")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/api/admin/products/{id}")
    public ProductResponse updateProduct(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequest request
    ) {
        return productService.updateProduct(id, request);
    }

    @PatchMapping("/api/admin/products/{id}/deactivate")
    public ProductResponse deactivateProduct(@PathVariable Long id) {
        return productService.deactivateProduct(id);
    }
}
