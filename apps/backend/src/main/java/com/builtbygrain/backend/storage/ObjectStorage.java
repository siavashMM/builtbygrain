package com.builtbygrain.backend.storage;

import java.time.Instant;

public interface ObjectStorage {

    void put(String key, byte[] bytes, String contentType, String sha256);

    ObjectData get(String key);

    ObjectMetadata head(String key);

    void delete(String key);

    record ObjectData(byte[] bytes, ObjectMetadata metadata) { }

    record ObjectMetadata(
        long contentLength,
        String contentType,
        String eTag,
        Instant lastModified,
        String sha256
    ) { }
}
