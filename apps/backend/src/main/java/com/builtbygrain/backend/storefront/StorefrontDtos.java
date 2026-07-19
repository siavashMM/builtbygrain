package com.builtbygrain.backend.storefront;

import java.time.Instant;
import java.util.List;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;
import com.builtbygrain.backend.product.ProductCardDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StorefrontDtos {
    private StorefrontDtos() {}

    public record StorefrontSettingsDto(
        String heroImageUrl,
        String heroImageAltText,
        String heroHeading,
        String heroSupportingText,
        Instant updatedAt
    ) {}

    public record StorefrontSettingsUpdate(
        @Size(max = 300) String heroImageAltText,
        @Size(max = 300) String heroHeading,
        @Size(max = 1000) String heroSupportingText
    ) {}

    public record NavigationGroupRequest(
        @NotBlank @Size(max = 160) String label,
        @NotNull Boolean active
    ) {}

    public record IdReference(@NotNull Long id) {}
    public record OrderedIds(@NotNull List<@NotNull Long> ids) {}

    public record NavigationGroupDto(
        Long id,
        String label,
        boolean active,
        int displayOrder,
        List<CategoryResponse> categories,
        List<ProductReferenceDto> featuredProducts
    ) {}

    public record ProductReferenceDto(Long id, String name, String slug, boolean active, String status) {}

    public record PublicCategoryDto(Long id, String name, String slug, String description, String path) {}

    public record PublicNavigationGroupDto(
        Long id,
        String label,
        int displayOrder,
        List<PublicCategoryDto> categories,
        List<ProductCardDto> featuredProducts
    ) {}

    public record PublicStorefrontDto(
        StorefrontSettingsDto settings,
        List<PublicNavigationGroupDto> navigationGroups
    ) {}
}
