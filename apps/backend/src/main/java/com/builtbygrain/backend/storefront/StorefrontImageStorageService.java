package com.builtbygrain.backend.storefront;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.builtbygrain.backend.product.ProductImageStorageService;

@Service
public class StorefrontImageStorageService {

    private final ProductImageStorageService images;

    public StorefrontImageStorageService(ProductImageStorageService images) {
        this.images = images;
    }

    public String store(MultipartFile image) {
        return images.store(image, "storefront");
    }

    public void delete(String imageUrl) {
        images.delete(imageUrl);
    }
}
