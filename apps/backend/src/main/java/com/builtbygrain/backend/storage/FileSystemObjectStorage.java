package com.builtbygrain.backend.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.object-storage.provider", havingValue = "filesystem")
public class FileSystemObjectStorage implements ObjectStorage {

    private final Path root;

    public FileSystemObjectStorage(
        @Value("${app.object-storage.filesystem-root:uploads}") String configuredRoot
    ) {
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType, String sha256) {
        Path destination = resolve(key);
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Files.writeString(metadataPath(destination), contentType + "\n" + sha256);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new ObjectStorageException("Object could not be stored.", exception);
        }
    }

    @Override
    public ObjectData get(String key) {
        Path object = requireObject(key);
        try {
            byte[] bytes = Files.readAllBytes(object);
            return new ObjectData(bytes, metadata(object, bytes.length));
        } catch (IOException exception) {
            throw new ObjectStorageException("Object could not be read.", exception);
        }
    }

    @Override
    public ObjectMetadata head(String key) {
        Path object = requireObject(key);
        try {
            return metadata(object, Files.size(object));
        } catch (IOException exception) {
            throw new ObjectStorageException("Object metadata could not be read.", exception);
        }
    }

    @Override
    public void delete(String key) {
        Path object = resolve(key);
        try {
            Files.deleteIfExists(object);
            Files.deleteIfExists(metadataPath(object));
        } catch (IOException exception) {
            throw new ObjectStorageException("Object could not be deleted.", exception);
        }
    }

    private ObjectMetadata metadata(Path object, long length) throws IOException {
        String contentType = Files.probeContentType(object);
        String sha256 = null;
        Path metadata = metadataPath(object);
        if (Files.exists(metadata)) {
            var lines = Files.readAllLines(metadata);
            if (!lines.isEmpty() && !lines.getFirst().isBlank()) contentType = lines.getFirst();
            if (lines.size() > 1 && !lines.get(1).isBlank()) sha256 = lines.get(1);
        }
        Instant modified = Files.getLastModifiedTime(object).toInstant();
        return new ObjectMetadata(length, contentType, sha256, modified, sha256);
    }

    private Path requireObject(String key) {
        Path object = resolve(key);
        if (!Files.isRegularFile(object)) {
            throw new ObjectStorageException("Object not found.", true);
        }
        return object;
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ObjectStorageException("Invalid object key.", false);
        }
        return resolved;
    }

    private Path metadataPath(Path object) {
        return object.resolveSibling(object.getFileName() + ".metadata");
    }
}
