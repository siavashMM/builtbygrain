package com.builtbygrain.backend.catalog;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class CatalogAdminDtos {
    private CatalogAdminDtos() {}

    public record StatusRequest(@NotNull Boolean active) {}
    public record ProductMoveRequest(@NotNull Long categoryId) {}

    public record OptionValueDto(Long id, String label, String code, String swatchHex, String swatchImageUrl,
        String extraLabel, int sortOrder, boolean active) {}
    public record OptionDto(Long id, Long productId, String name, String code, String displayType,
        int sortOrder, boolean required, List<OptionValueDto> values) {}
    public record OptionRequest(@NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max=80) String code,
        @NotBlank @Pattern(regexp="COLOR_SWATCH|BUTTON|DROPDOWN") String displayType,
        @Min(0) int sortOrder, @NotNull Boolean required) {}
    public record OptionValueRequest(@NotBlank @Size(max=100) String label,
        @NotBlank @Pattern(regexp="^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max=80) String code,
        @Pattern(regexp="^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$") String swatchHex,
        @Size(max=2048) String swatchImageUrl, @Size(max=160) String extraLabel,
        @Min(0) int sortOrder, @NotNull Boolean active) {}

    public record VariantOptionValue(Long optionId, String optionName, Long valueId, String label) {}
    public record VariantDto(Long id, String publicId, Long productId, String sku, String label,
        List<VariantOptionValue> optionValues, long regularPriceCents, Long salePriceCents,
        int stockQuantity, String availabilityStatus, boolean active, boolean allowBackorder,
        String deliveryEstimate, String primaryImageUrl, List<String> imageUrls) {}
    public record GeneratePreview(int combinationCount, int existingCount, int createCount) {}
    public record GenerateRequest(Long defaultPriceCents, Boolean deactivateRemoved) {}
    public record VariantUpdateRequest(@Size(max=120) String sku, @PositiveOrZero long regularPriceCents,
        @PositiveOrZero Long salePriceCents, @PositiveOrZero int stockQuantity,
        @NotBlank @Pattern(regexp="IN_STOCK|LOW_STOCK|OUT_OF_STOCK|BACKORDER|PREORDER|DISCONTINUED") String availabilityStatus,
        @NotNull Boolean active, @NotNull Boolean allowBackorder, @Size(max=160) String deliveryEstimate) {
        @AssertTrue(message = "Sale price must not exceed regular price")
        public boolean pricesAreCoherent() {
            return salePriceCents == null || salePriceCents <= regularPriceCents;
        }
    }
    public record BulkVariantUpdateRequest(@NotEmpty List<@NotNull Long> variantIds,
        @PositiveOrZero Long regularPriceCents, Long priceDeltaCents, @DecimalMin("-100") Double pricePercent,
        @PositiveOrZero Long salePriceCents, @PositiveOrZero Integer stockQuantity, Integer addStock,
        @Pattern(regexp="IN_STOCK|LOW_STOCK|OUT_OF_STOCK|BACKORDER|PREORDER|DISCONTINUED") String availabilityStatus,
        Boolean active, Boolean allowBackorder, @Size(max=160) String deliveryEstimate) {
        @AssertTrue(message = "Sale price must not exceed regular price")
        public boolean pricesAreCoherent() {
            return salePriceCents == null || regularPriceCents == null || salePriceCents <= regularPriceCents;
        }
    }

    public record ProductImageDto(Long id, String url, String altText, String filename, int sortOrder,
        boolean shared, boolean active, List<String> usages) {}
    public record ListingImagesDto(Long primaryImageId, Long hoverImageId) {}
    public record ListingImageRequest(Long imageId) {}
    public record AssignVariantImageRequest(@NotNull Long imageId, @Min(0) int sortOrder, @NotNull Boolean primary) {}
}
