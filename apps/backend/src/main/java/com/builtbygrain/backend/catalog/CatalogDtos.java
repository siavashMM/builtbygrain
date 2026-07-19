package com.builtbygrain.backend.catalog;

import java.util.List;
import jakarta.validation.constraints.*;
import com.builtbygrain.backend.product.ProductCardDto;

public final class CatalogDtos {
    private CatalogDtos() {}
    public enum NodeType { CATEGORY, PRODUCT, VARIANT }
    public record TreeNode(String id, NodeType nodeType, String parentId, String label, String secondaryLabel,
                           String status, boolean hasChildren, String iconType, int sortOrder) {}
    public record CategoryRequest(@NotBlank @Size(max=160) String name,
        @NotBlank @Pattern(regexp="^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max=180) String slug,
        Long parentId, @Size(max=1000) String description, @Size(max=2048) String imageUrl,
        @Min(0) Integer sortOrder, @NotNull Boolean active) {}
    public record MoveRequest(Long parentId, @Min(0) int sortOrder) {}
    public record CategoryCreateRequest(@NotBlank @Size(max=160) String name, Long parentId) {}
    public record CategoryNameRequest(@NotBlank @Size(max=160) String name) {}
    public record CategoryParentRequest(Long parentId, @Min(0) Integer position) {}
    public record CategoryPositionRequest(@Min(0) int position) {}
    public record CategoryOption(Long id, String name, String path, boolean active, int depth) {}
    public record AdminCategoryNode(Long id, Long parentId, String name, String slug, boolean active,
        int position, long directProductCount, List<AdminCategoryNode> children) {}
    public record NavigationCategory(Long id, String name, String slug, String path,
        List<NavigationCategory> children) {}
    public record CategoryResponse(Long id, Long parentId, String name, String slug, String description,
        String imageUrl, String path, int sortOrder, boolean active) {
        public static CategoryResponse from(Category c) { return new CategoryResponse(c.getId(), c.getParent()==null?null:c.getParent().getId(),
            c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(), publicPath(c), c.getSortOrder(), c.isActive()); }
        private static String publicPath(Category category) {
            java.util.ArrayList<String> slugs = new java.util.ArrayList<>();
            for (Category cursor = category; cursor != null; cursor = cursor.getParent()) slugs.add(cursor.getSlug());
            java.util.Collections.reverse(slugs);
            return "/category/" + String.join("/", slugs);
        }
    }
    public record CategoryPageResponse(CategoryResponse category, List<CategoryResponse> breadcrumbs,
        List<ProductCardDto> products) {}
}
