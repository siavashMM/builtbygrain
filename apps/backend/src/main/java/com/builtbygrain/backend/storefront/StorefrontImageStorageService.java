package com.builtbygrain.backend.storefront;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.builtbygrain.backend.product.ProductImageStorageException;
import com.builtbygrain.backend.product.ProductImageStorageService;

@Service
public class StorefrontImageStorageService {
    private static final String PUBLIC_PREFIX = "/api/public/uploads/storefront/";
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif"
    );

    private final Path storefrontRoot;
    private final ProductImageStorageService productImages;

    public StorefrontImageStorageService(
        @Value("${app.uploads.root:uploads}") String uploadRoot,
        ProductImageStorageService productImages
    ) {
        this.storefrontRoot = Path.of(uploadRoot).toAbsolutePath().normalize().resolve("storefront").normalize();
        this.productImages = productImages;
    }

    public String store(MultipartFile image) {
        productImages.validateImage(image);
        String filename = UUID.randomUUID() + EXTENSIONS.getOrDefault(image.getContentType(), ".img");
        Path destination = storefrontRoot.resolve(filename).normalize();
        if (!destination.startsWith(storefrontRoot)) {
            throw new ProductImageStorageException("Invalid image destination.");
        }
        try {
            Files.createDirectories(storefrontRoot);
            try (InputStream input = image.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProductImageStorageException("Homepage hero image could not be saved.");
        }
        return PUBLIC_PREFIX + filename;
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(PUBLIC_PREFIX)) return;
        Path image = storefrontRoot.resolve(imageUrl.substring(PUBLIC_PREFIX.length())).normalize();
        if (!image.startsWith(storefrontRoot)) return;
        try {
            Files.deleteIfExists(image);
        } catch (IOException exception) {
            throw new ProductImageStorageException("Homepage hero image could not be deleted.");
        }
    }
}
