package com.builtbygrain.backend.product;

import java.util.List;

public record ProductCardDto(
    Long id,
    String name,
    String slug,
    String currency,
    long fromPriceCents,
    String primaryImageUrl,
    String hoverImageUrl,
    Long categoryId,
    String categoryName,
    String categorySlug,
    List<ColorSwatch> colorSwatches
) {
    public record ColorSwatch(
        Long id,
        String label,
        String swatchHex,
        String swatchImageUrl,
        String primaryImageUrl,
        String hoverImageUrl
    ) {}
}
