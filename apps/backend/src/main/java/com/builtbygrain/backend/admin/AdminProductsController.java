package com.builtbygrain.backend.admin;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.builtbygrain.backend.product.ProductRequest;
import com.builtbygrain.backend.product.ProductResponse;
import com.builtbygrain.backend.product.ProductService;

@RestController
public class AdminProductsController {

    private final ProductService productService;

    public AdminProductsController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/admin/products")
    public List<ProductResponse> products() {
        return productService.getAllProductsForAdmin();
    }

    @PostMapping("/api/admin/products")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @PostMapping(path = "/api/admin/products/with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse createProductWithImages(
        @Valid @RequestPart("product") ProductRequest request,
        @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return productService.createProduct(request, images);
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

    @PatchMapping("/api/admin/products/{id}/activate")
    public ProductResponse activateProduct(@PathVariable Long id) {
        return productService.activateProduct(id);
    }

    @DeleteMapping("/api/admin/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    @PostMapping(
        path = "/api/admin/products/{id}/images",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ProductResponse uploadProductImages(
        @PathVariable Long id,
        @RequestParam("images") List<MultipartFile> images
    ) {
        return productService.addProductImages(id, images);
    }

    @DeleteMapping("/api/admin/products/{id}/images/{imageIndex}")
    public ProductResponse removeProductImage(@PathVariable Long id, @PathVariable int imageIndex) {
        return productService.removeProductImage(id, imageIndex);
    }
}
