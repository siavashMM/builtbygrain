package com.builtbygrain.backend.product;

public record ProductResponse(
    Long id,
    String name,
    String description,
    long priceCents,
    String currency,
    boolean active
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPriceCents(),
            product.getCurrency(),
            product.isActive()
        );
    }
}
