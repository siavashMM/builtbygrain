package com.builtbygrain.backend.product;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif"
    );

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "image/gif", ".gif"
    );

    private final Path uploadRoot;
    private final long maxFileSizeBytes;

    public ProductImageStorageService(
        @Value("${app.uploads.root:uploads}") String uploadRoot,
        @Value("${app.uploads.max-file-size-bytes:5242880}") long maxFileSizeBytes
    ) {
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String store(MultipartFile image) {
        validateImage(image);

        String filename = UUID.randomUUID() + extensionFor(image);
        Path productUploadRoot = uploadRoot.resolve("products").normalize();
        Path destination = productUploadRoot.resolve(filename).normalize();

        if (!destination.startsWith(productUploadRoot)) {
            throw new ProductImageStorageException("Invalid image destination.");
        }

        try {
            Files.createDirectories(productUploadRoot);
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProductImageStorageException("Product image could not be saved.");
        }

        return "/api/public/uploads/products/" + filename;
    }

    public void delete(String imageUrl) {
        String legacyPrefix = "/uploads/products/";
        String publicPrefix = "/api/public/uploads/products/";
        if (imageUrl == null || (!imageUrl.startsWith(legacyPrefix) && !imageUrl.startsWith(publicPrefix))) {
            return;
        }
        String prefix = imageUrl.startsWith(publicPrefix) ? publicPrefix : legacyPrefix;
        Path productRoot = uploadRoot.resolve("products").normalize();
        Path image = productRoot.resolve(imageUrl.substring(prefix.length())).normalize();
        if (!image.startsWith(productRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(image);
        } catch (IOException exception) {
            throw new ProductImageStorageException("Product image could not be deleted.");
        }
    }

    public void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ProductImageStorageException("Product image is required.");
        }

        if (image.getSize() > maxFileSizeBytes) {
            throw new ProductImageStorageException("Product image is too large.");
        }

        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ProductImageStorageException("Product image must be JPEG, PNG, WebP, or GIF.");
        }
    }

    private String extensionFor(MultipartFile image) {
        return EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(image.getContentType(), ".img");
    }
}
