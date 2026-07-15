package com.builtbygrain.backend.catalog;

import jakarta.validation.constraints.*;

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
    public record CategoryResponse(Long id, Long parentId, String name, String slug, String description,
        String imageUrl, int sortOrder, boolean active) {
        public static CategoryResponse from(Category c) { return new CategoryResponse(c.getId(), c.getParent()==null?null:c.getParent().getId(),
            c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(), c.getSortOrder(), c.isActive()); }
    }
}
