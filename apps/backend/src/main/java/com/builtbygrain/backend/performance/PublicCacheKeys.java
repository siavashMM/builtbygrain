package com.builtbygrain.backend.performance;

public final class PublicCacheKeys {

    private PublicCacheKeys() {}

    public static String categoryPath(String rawPath) {
        if (rawPath == null) return "page:";
        return "page:" + rawPath.strip().replaceAll("^/+|/+$", "");
    }
}
