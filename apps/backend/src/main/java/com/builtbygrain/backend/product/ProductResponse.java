package com.builtbygrain.backend.product;

import java.util.List;

public record ProductResponse(
    Long id,
    String name,
    String slug,
    String description,
    long priceCents,
    String currency,
    String imageUrl,
    List<String> imageUrls,
    boolean inStock,
    List<String> sizes,
    boolean active
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getPriceCents(),
            product.getCurrency(),
            normalizeImageUrl(product.getImageUrl()),
            product.getImageUrls().stream().map(ProductResponse::normalizeImageUrl).toList(),
            product.isInStock(),
            product.getSizes(),
            product.isActive()
        );
    }

    private static String normalizeImageUrl(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("/uploads/")) {
            return "/api/public" + imageUrl;
        }
        return imageUrl;
    }
}
