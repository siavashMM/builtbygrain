package com.builtbygrain.backend.shop;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.builtbygrain.backend.product.ProductResponse;
import com.builtbygrain.backend.product.ProductService;

@RestController
public class PublicProductsController {

    private final ProductService productService;

    public PublicProductsController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/public/products")
    public List<ProductResponse> activeProducts() {
        return productService.getActiveProducts();
    }

    @GetMapping("/api/public/products/{slug}")
    public ProductResponse activeProductBySlug(@PathVariable String slug) {
        return productService.getActiveProductBySlug(slug);
    }
}
