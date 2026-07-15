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
    boolean active,
    ProductConfiguration configuration,
    Long categoryId,
    String categoryName,
    String categorySlug
) {
    public static ProductResponse from(Product product) {
        return from(product, product.getConfiguration());
    }

    public static ProductResponse from(Product product, ProductConfiguration configuration) {
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
            product.isActive(),
            withDefaultVariant(product, configuration),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.getCategory().getSlug()
        );
    }

    private static ProductConfiguration withDefaultVariant(Product product, ProductConfiguration config) {
        if (!config.variants().isEmpty()) return config;
        var defaultVariant = new ProductConfiguration.Variant(
            "default-" + product.getId(), null, List.of(), product.getPriceCents(), config.salePriceCents(),
            product.isInStock() ? 99 : 0, product.isInStock() ? "IN_STOCK" : "OUT_OF_STOCK",
            product.isInStock(), false, false, product.getImageUrls(), config.deliveryEstimate()
        );
        return new ProductConfiguration(config.subtitle(), config.badge(), config.salePriceCents(), config.unitPriceLabel(),
            config.deliveryEstimate(), config.benefits(), config.specifications(), config.sections(), config.faqs(),
            config.options(), List.of(defaultVariant), config.rating(), config.sizeAffectsImages());
    }

    private static String normalizeImageUrl(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("/uploads/")) {
            return "/api/public" + imageUrl;
        }
        return imageUrl;
    }
}
