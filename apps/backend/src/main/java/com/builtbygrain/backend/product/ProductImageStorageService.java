package com.builtbygrain.backend.product;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.builtbygrain.backend.storage.ObjectStorage;
import com.builtbygrain.backend.storage.ObjectStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageStorageService.class);
    private static final String PUBLIC_PREFIX = "/api/public/uploads/";
    private static final Pattern MANAGED_URL = Pattern.compile(
        "^/(?:api/public/)?uploads/((?:products|storefront)/[0-9a-fA-F-]{36}\\.(?:jpg|png|webp|gif))$"
    );
    private static final Set<String> NAMESPACES = Set.of("products", "storefront");

    private final ObjectStorage storage;
    private final long maxFileSizeBytes;

    public ProductImageStorageService(
        ObjectStorage storage,
        @Value("${app.uploads.max-file-size-bytes:5242880}") long maxFileSizeBytes
    ) {
        this.storage = storage;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String store(MultipartFile image) {
        return store(image, "products");
    }

    public String store(MultipartFile image, String namespace) {
        if (!NAMESPACES.contains(namespace)) {
            throw new ProductImageStorageException("Invalid image namespace.");
        }
        ValidatedImage validated = validateAndRead(image);
        String key = namespace + "/" + UUID.randomUUID() + validated.extension();
        try {
            storage.put(key, validated.bytes(), validated.contentType(), sha256(validated.bytes()));
            deleteOnRollback(key);
            return PUBLIC_PREFIX + key;
        } catch (ObjectStorageException exception) {
            throw new ProductImageStorageException("Image could not be saved.");
        }
    }

    public void delete(String imageUrl) {
        String key = keyFromManagedUrl(imageUrl);
        if (key == null) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key, "after database commit");
                }
            });
        } else {
            deleteNow(key);
        }
    }

    public void validateImage(MultipartFile image) {
        validateAndRead(image);
    }

    private ValidatedImage validateAndRead(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ProductImageStorageException("Product image is required.");
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException exception) {
            throw new ProductImageStorageException("Product image could not be read.");
        }
        if (bytes.length > maxFileSizeBytes) {
            throw new ProductImageStorageException("Product image is too large.");
        }

        DetectedImage detected = detect(bytes);
        if (detected == null) {
            throw new ProductImageStorageException("Product image must be JPEG, PNG, WebP, or GIF.");
        }

        String declaredType = image.getContentType();
        if (StringUtils.hasText(declaredType) && !detected.contentType().equalsIgnoreCase(declaredType)) {
            throw new ProductImageStorageException("Product image content does not match its media type.");
        }
        return new ValidatedImage(bytes, detected.contentType(), detected.extension());
    }

    private DetectedImage detect(byte[] bytes) {
        if (startsWith(bytes, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return new DetectedImage("image/jpeg", ".jpg");
        }
        if (startsWith(bytes, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return new DetectedImage("image/png", ".png");
        }
        if (startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
            || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return new DetectedImage("image/gif", ".gif");
        }
        if (bytes.length >= 12
            && startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
            && matchesAt(bytes, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return new DetectedImage("image/webp", ".webp");
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return matchesAt(bytes, 0, prefix);
    }

    private boolean matchesAt(byte[] bytes, int offset, byte[] expected) {
        if (bytes.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) return false;
        }
        return true;
    }

    private void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteQuietly(key, "after database rollback");
            }
        });
    }

    private void deleteNow(String key) {
        try {
            storage.delete(key);
        } catch (ObjectStorageException exception) {
            throw new ProductImageStorageException("Image could not be deleted.");
        }
    }

    private void deleteQuietly(String key, String phase) {
        try {
            storage.delete(key);
        } catch (ObjectStorageException exception) {
            // The database outcome is already final. Surface this in operations telemetry so
            // reconciliation can retry it without turning a committed mutation into a false 500.
            LOGGER.error("Could not delete object {} {}", key, phase, exception);
        }
    }

    private String keyFromManagedUrl(String imageUrl) {
        if (imageUrl == null) return null;
        Matcher match = MANAGED_URL.matcher(imageUrl);
        return match.matches() ? match.group(1) : null;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record DetectedImage(String contentType, String extension) { }
    private record ValidatedImage(byte[] bytes, String contentType, String extension) { }
}
