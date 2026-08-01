package com.builtbygrain.backend.storage;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicUploadController {

    private static final Set<String> NAMESPACES = Set.of("products", "storefront");
    private static final Pattern FILENAME = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp|gif)$"
    );
    private static final CacheControl IMMUTABLE_CACHE = CacheControl.maxAge(java.time.Duration.ofDays(365))
        .cachePublic()
        .immutable();

    private final ObjectStorage storage;

    public PublicUploadController(ObjectStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/public/uploads/{namespace}/{filename}")
    public ResponseEntity<byte[]> get(
        @PathVariable String namespace,
        @PathVariable String filename,
        @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
        @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSince
    ) {
        ObjectStorage.ObjectData object = object(namespace, filename);
        ObjectStorage.ObjectMetadata metadata = object.metadata();
        String eTag = quote(metadata.eTag() == null ? metadata.sha256() : metadata.eTag());

        if (matches(ifNoneMatch, eTag) || notModified(ifModifiedSince, metadata.lastModified())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(eTag)
                .cacheControl(IMMUTABLE_CACHE)
                .build();
        }

        byte[] bytes = object.bytes();
        ByteRange requested = parseRange(range, bytes.length);
        HttpHeaders headers = headers(metadata, eTag);
        if (requested == null) {
            headers.setContentLength(bytes.length);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        }

        byte[] partial = Arrays.copyOfRange(bytes, requested.start(), requested.end() + 1);
        headers.setContentLength(partial.length);
        headers.set(HttpHeaders.CONTENT_RANGE,
            "bytes " + requested.start() + "-" + requested.end() + "/" + bytes.length);
        return new ResponseEntity<>(partial, headers, HttpStatus.PARTIAL_CONTENT);
    }

    @RequestMapping(path = "/api/public/uploads/{namespace}/{filename}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(
        @PathVariable String namespace,
        @PathVariable String filename,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        ObjectStorage.ObjectMetadata metadata = metadata(namespace, filename);
        String eTag = quote(metadata.eTag() == null ? metadata.sha256() : metadata.eTag());
        HttpHeaders headers = headers(metadata, eTag);
        if (matches(ifNoneMatch, eTag)) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }
        headers.setContentLength(metadata.contentLength());
        return new ResponseEntity<>(headers, HttpStatus.OK);
    }

    private ObjectStorage.ObjectData object(String namespace, String filename) {
        validate(namespace, filename);
        try {
            return storage.get(namespace + "/" + filename);
        } catch (ObjectStorageException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                exception.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE,
                exception.isNotFound() ? "Image not found" : "Image storage unavailable"
            );
        }
    }

    private ObjectStorage.ObjectMetadata metadata(String namespace, String filename) {
        validate(namespace, filename);
        try {
            return storage.head(namespace + "/" + filename);
        } catch (ObjectStorageException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                exception.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE,
                exception.isNotFound() ? "Image not found" : "Image storage unavailable"
            );
        }
    }

    private void validate(String namespace, String filename) {
        if (!NAMESPACES.contains(namespace) || !FILENAME.matcher(filename).matches()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
    }

    private HttpHeaders headers(ObjectStorage.ObjectMetadata metadata, String eTag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(IMMUTABLE_CACHE);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (metadata.contentType() != null) {
            headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        }
        if (eTag != null) headers.setETag(eTag);
        if (metadata.lastModified() != null) headers.setLastModified(metadata.lastModified());
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    private String quote(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.replace("\"", "");
        return "\"" + clean + "\"";
    }

    private boolean matches(String candidate, String eTag) {
        return candidate != null && eTag != null
            && Arrays.stream(candidate.split(",")).map(String::trim)
                .anyMatch(value -> value.equals("*") || value.equals(eTag));
    }

    private boolean notModified(String value, Instant lastModified) {
        if (value == null || lastModified == null) return false;
        try {
            Instant requestTime = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return lastModified.toEpochMilli() / 1000 <= requestTime.toEpochMilli() / 1000;
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }

    private ByteRange parseRange(String value, int length) {
        if (value == null || value.isBlank()) return null;
        if (!value.startsWith("bytes=") || value.contains(",")) throw rangeNotSatisfiable(length);
        String[] parts = value.substring(6).split("-", -1);
        if (parts.length != 2) throw rangeNotSatisfiable(length);
        try {
            int start;
            int end;
            if (parts[0].isBlank()) {
                int suffixLength = Integer.parseInt(parts[1]);
                if (suffixLength <= 0) throw rangeNotSatisfiable(length);
                start = Math.max(0, length - suffixLength);
                end = length - 1;
            } else {
                start = Integer.parseInt(parts[0]);
                end = parts[1].isBlank() ? length - 1 : Integer.parseInt(parts[1]);
            }
            if (start < 0 || start >= length || end < start) throw rangeNotSatisfiable(length);
            return new ByteRange(start, Math.min(end, length - 1));
        } catch (NumberFormatException exception) {
            throw rangeNotSatisfiable(length);
        }
    }

    private org.springframework.web.server.ResponseStatusException rangeNotSatisfiable(int length) {
        return new org.springframework.web.server.ResponseStatusException(
            HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
            "Requested range is not satisfiable for an object of " + length + " bytes"
        );
    }

    private record ByteRange(int start, int end) { }
}
