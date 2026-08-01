package com.builtbygrain.backend.storage;

public class ObjectStorageException extends RuntimeException {

    private final boolean notFound;

    public ObjectStorageException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
        this.notFound = false;
    }

    public boolean isNotFound() {
        return notFound;
    }
}
