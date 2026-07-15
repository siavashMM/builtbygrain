package com.builtbygrain.backend.shop;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import com.builtbygrain.backend.product.ProductResponse;
import com.builtbygrain.backend.product.ProductCardDto;
import com.builtbygrain.backend.product.ProductCardService;
import com.builtbygrain.backend.product.ProductService;

@RestController
public class PublicProductsController {

    private final ProductService productService;
    private final ProductCardService productCardService;

    public PublicProductsController(ProductService productService, ProductCardService productCardService) {
        this.productService = productService;
        this.productCardService = productCardService;
    }

    @GetMapping("/api/public/products")
    public List<ProductCardDto> activeProducts() {
        return productCardService.activeCards();
    }

    @GetMapping("/api/public/products/{slug}")
    public ResponseEntity<ProductResponse> activeProductBySlug(@PathVariable String slug,
            @RequestParam(required = false) String variant) {
        ProductResponse product = productService.getActiveProductBySlug(slug);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(product);
    }
}
