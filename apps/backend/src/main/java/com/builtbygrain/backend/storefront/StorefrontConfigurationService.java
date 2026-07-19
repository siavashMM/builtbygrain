package com.builtbygrain.backend.storefront;

import static com.builtbygrain.backend.storefront.StorefrontDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;
import com.builtbygrain.backend.catalog.Category;
import com.builtbygrain.backend.catalog.CategoryRepository;
import com.builtbygrain.backend.product.Product;
import com.builtbygrain.backend.product.ProductCardDto;
import com.builtbygrain.backend.product.ProductCardService;
import com.builtbygrain.backend.product.ProductRepository;

@Service
public class StorefrontConfigurationService {
    public static final int MAX_FEATURED_PRODUCTS = 3;

    private final JdbcTemplate jdbc;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductCardService productCards;
    private final StorefrontImageStorageService images;

    public StorefrontConfigurationService(JdbcTemplate jdbc, CategoryRepository categories,
        ProductRepository products, ProductCardService productCards, StorefrontImageStorageService images) {
        this.jdbc = jdbc;
        this.categories = categories;
        this.products = products;
        this.productCards = productCards;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public StorefrontSettingsDto settings() {
        return jdbc.queryForObject("SELECT * FROM storefront_settings WHERE id = 1", this::settingsRow);
    }

    @Transactional
    public StorefrontSettingsDto updateSettings(StorefrontSettingsUpdate request) {
        String currentImage = jdbc.queryForObject("SELECT hero_image_url FROM storefront_settings WHERE id = 1 FOR UPDATE", String.class);
        String alt = clean(request.heroImageAltText());
        if (currentImage != null && alt == null) {
            throw badRequest("Hero image alt text is required while a hero image is configured.");
        }
        jdbc.update("""
            UPDATE storefront_settings
            SET hero_image_alt_text = ?, hero_heading = ?, hero_supporting_text = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
            """, alt, clean(request.heroHeading()), clean(request.heroSupportingText()));
        return settings();
    }

    @Transactional
    public StorefrontSettingsDto replaceHeroImage(MultipartFile image, String altText) {
        String alt = clean(altText);
        if (alt == null) throw badRequest("Hero image alt text is required.");
        if (alt.length() > 300) throw badRequest("Hero image alt text must be at most 300 characters.");

        String newUrl = images.store(image);
        String oldUrl;
        try {
            oldUrl = replaceHeroImageRecord(newUrl, alt);
        } catch (RuntimeException exception) {
            images.delete(newUrl);
            throw exception;
        }
        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            try {
                images.delete(oldUrl);
            } catch (RuntimeException exception) {
                try {
                    images.delete(newUrl);
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }
        }
        return settings();
    }

    private String replaceHeroImageRecord(String imageUrl, String altText) {
        String oldUrl = jdbc.queryForObject("SELECT hero_image_url FROM storefront_settings WHERE id = 1 FOR UPDATE", String.class);
        jdbc.update("""
            UPDATE storefront_settings SET hero_image_url = ?, hero_image_alt_text = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
            """, imageUrl, altText);
        return oldUrl;
    }

    @Transactional(readOnly = true)
    public List<NavigationGroupDto> groups() {
        return groupRows(false).stream().map(this::adminGroup).toList();
    }

    @Transactional
    public NavigationGroupDto createGroup(NavigationGroupRequest request) {
        lockStorefront();
        String label = clean(request.label());
        int order = jdbc.queryForObject("SELECT COUNT(*) FROM navigation_groups", Integer.class);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO navigation_groups(label, active, display_order, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new String[] {"id"});
            statement.setString(1, label);
            statement.setBoolean(2, request.active());
            statement.setInt(3, order);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Navigation group ID was not generated.");
        return group(id.longValue());
    }

    @Transactional
    public NavigationGroupDto updateGroup(long groupId, NavigationGroupRequest request) {
        requireGroup(groupId);
        jdbc.update("UPDATE navigation_groups SET label = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            clean(request.label()), request.active(), groupId);
        return group(groupId);
    }

    @Transactional
    public NavigationGroupDto setGroupActive(long groupId, boolean active) {
        requireGroup(groupId);
        jdbc.update("UPDATE navigation_groups SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", active, groupId);
        return group(groupId);
    }

    @Transactional
    public void deleteGroup(long groupId) {
        lockStorefront();
        requireGroup(groupId);
        jdbc.update("DELETE FROM navigation_groups WHERE id = ?", groupId);
        normalizeGroupOrder();
    }

    @Transactional
    public List<NavigationGroupDto> reorderGroups(OrderedIds request) {
        lockStorefront();
        List<Long> current = groupRows(false).stream().map(GroupRow::id).toList();
        validateCompleteOrder(request.ids(), current, "navigation groups");
        for (int index = 0; index < request.ids().size(); index++) {
            jdbc.update("UPDATE navigation_groups SET display_order = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                index, request.ids().get(index));
        }
        return groups();
    }

    @Transactional
    public NavigationGroupDto assignCategory(long groupId, IdReference request) {
        requireGroupForUpdate(groupId);
        requireCategory(request.id());
        Integer next = jdbc.queryForObject("SELECT COUNT(*) FROM navigation_group_categories WHERE navigation_group_id = ?", Integer.class, groupId);
        try {
            jdbc.update("INSERT INTO navigation_group_categories(navigation_group_id, category_id, display_order) VALUES (?, ?, ?)",
                groupId, request.id(), next);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Category is already assigned to this navigation group.");
        }
        touch(groupId);
        return group(groupId);
    }

    @Transactional
    public void removeCategory(long groupId, long categoryId) {
        requireGroup(groupId);
        if (jdbc.update("DELETE FROM navigation_group_categories WHERE navigation_group_id = ? AND category_id = ?", groupId, categoryId) == 0) {
            throw notFound("Category assignment not found.");
        }
        normalizeAssignmentOrder("navigation_group_categories", "category_id", groupId);
        touch(groupId);
    }

    @Transactional
    public NavigationGroupDto reorderCategories(long groupId, OrderedIds request) {
        requireGroupForUpdate(groupId);
        List<Long> current = assignedIds("navigation_group_categories", "category_id", groupId);
        validateCompleteOrder(request.ids(), current, "assigned categories");
        updateAssignmentOrder("navigation_group_categories", "category_id", groupId, request.ids());
        touch(groupId);
        return group(groupId);
    }

    @Transactional
    public NavigationGroupDto assignFeaturedProduct(long groupId, IdReference request) {
        requireGroupForUpdate(groupId);
        requireProduct(request.id());
        List<Long> current = assignedIds("navigation_group_featured_products", "product_id", groupId);
        if (current.contains(request.id())) throw conflict("Product is already featured in this navigation group.");
        if (current.size() >= MAX_FEATURED_PRODUCTS) {
            throw conflict("A navigation group may contain at most three featured products.");
        }
        jdbc.update("INSERT INTO navigation_group_featured_products(navigation_group_id, product_id, display_order) VALUES (?, ?, ?)",
            groupId, request.id(), current.size());
        touch(groupId);
        return group(groupId);
    }

    @Transactional
    public void removeFeaturedProduct(long groupId, long productId) {
        requireGroup(groupId);
        if (jdbc.update("DELETE FROM navigation_group_featured_products WHERE navigation_group_id = ? AND product_id = ?", groupId, productId) == 0) {
            throw notFound("Featured product assignment not found.");
        }
        normalizeAssignmentOrder("navigation_group_featured_products", "product_id", groupId);
        touch(groupId);
    }

    @Transactional
    public NavigationGroupDto reorderFeaturedProducts(long groupId, OrderedIds request) {
        requireGroupForUpdate(groupId);
        List<Long> current = assignedIds("navigation_group_featured_products", "product_id", groupId);
        validateCompleteOrder(request.ids(), current, "featured products");
        updateAssignmentOrder("navigation_group_featured_products", "product_id", groupId, request.ids());
        touch(groupId);
        return group(groupId);
    }

    @Transactional(readOnly = true)
    public PublicStorefrontDto publicStorefront() {
        Map<Long, ProductCardDto> cards = productCards.activeCards().stream()
            .collect(Collectors.toMap(ProductCardDto::id, Function.identity()));
        List<PublicNavigationGroupDto> publicGroups = groupRows(true).stream().map(row -> {
            List<PublicCategoryDto> publicCategories = assignedCategories(row.id()).stream()
                .filter(this::hasActiveAncestry)
                .map(category -> new PublicCategoryDto(category.getId(), category.getName(), category.getSlug(),
                    category.getDescription(), "/category/" + category.path()))
                .toList();
            List<ProductCardDto> featured = assignedProductIds(row.id()).stream().map(cards::get)
                .filter(java.util.Objects::nonNull).toList();
            return new PublicNavigationGroupDto(row.id(), row.label(), row.displayOrder(), publicCategories, featured);
        }).toList();
        return new PublicStorefrontDto(settings(), publicGroups);
    }

    private NavigationGroupDto group(long id) {
        GroupRow row = groupRows(false).stream().filter(item -> item.id() == id).findFirst()
            .orElseThrow(() -> notFound("Navigation group not found."));
        return adminGroup(row);
    }

    private NavigationGroupDto adminGroup(GroupRow row) {
        List<CategoryResponse> categoryDtos = assignedCategories(row.id()).stream().map(CategoryResponse::from).toList();
        List<ProductReferenceDto> productDtos = assignedProducts(row.id()).stream()
            .map(product -> new ProductReferenceDto(product.getId(), product.getName(), product.getSlug(),
                product.isActive(), product.getStatus().name())).toList();
        return new NavigationGroupDto(row.id(), row.label(), row.active(), row.displayOrder(), categoryDtos, productDtos);
    }

    private List<GroupRow> groupRows(boolean activeOnly) {
        String where = activeOnly ? " WHERE active = TRUE" : "";
        return jdbc.query("SELECT id, label, active, display_order FROM navigation_groups" + where + " ORDER BY display_order, id",
            (rs, row) -> new GroupRow(rs.getLong("id"), rs.getString("label"), rs.getBoolean("active"), rs.getInt("display_order")));
    }

    private List<Category> assignedCategories(long groupId) {
        List<Long> ids = assignedIds("navigation_group_categories", "category_id", groupId);
        Map<Long, Category> values = categories.findAllById(ids).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        return ids.stream().map(values::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Product> assignedProducts(long groupId) {
        List<Long> ids = assignedProductIds(groupId);
        Map<Long, Product> values = products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(values::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Long> assignedProductIds(long groupId) {
        return assignedIds("navigation_group_featured_products", "product_id", groupId);
    }

    private List<Long> assignedIds(String table, String idColumn, long groupId) {
        return jdbc.queryForList("SELECT " + idColumn + " FROM " + table
            + " WHERE navigation_group_id = ? ORDER BY display_order, " + idColumn, Long.class, groupId);
    }

    private void updateAssignmentOrder(String table, String idColumn, long groupId, List<Long> ids) {
        for (int index = 0; index < ids.size(); index++) {
            jdbc.update("UPDATE " + table + " SET display_order = ? WHERE navigation_group_id = ? AND " + idColumn + " = ?",
                index, groupId, ids.get(index));
        }
    }

    private void normalizeAssignmentOrder(String table, String idColumn, long groupId) {
        updateAssignmentOrder(table, idColumn, groupId, assignedIds(table, idColumn, groupId));
    }

    private void normalizeGroupOrder() {
        List<Long> ids = groupRows(false).stream().map(GroupRow::id).toList();
        for (int index = 0; index < ids.size(); index++) {
            jdbc.update("UPDATE navigation_groups SET display_order = ? WHERE id = ?", index, ids.get(index));
        }
    }

    private void validateCompleteOrder(List<Long> requested, List<Long> current, String resource) {
        if (new HashSet<>(requested).size() != requested.size()) throw badRequest("Reorder IDs must not contain duplicates.");
        if (requested.size() != current.size() || !new HashSet<>(requested).equals(new HashSet<>(current))) {
            throw badRequest("Reorder request must contain every current " + resource + " ID exactly once.");
        }
    }

    private void requireGroup(long groupId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM navigation_groups WHERE id = ?", Integer.class, groupId);
        if (count == null || count == 0) throw notFound("Navigation group not found.");
    }

    private void lockStorefront() {
        jdbc.queryForObject("SELECT id FROM storefront_settings WHERE id = 1 FOR UPDATE", Short.class);
    }

    private void requireGroupForUpdate(long groupId) {
        if (jdbc.queryForList("SELECT id FROM navigation_groups WHERE id = ? FOR UPDATE", Long.class, groupId).isEmpty()) {
            throw notFound("Navigation group not found.");
        }
    }

    private Category requireCategory(long id) {
        return categories.findById(id).orElseThrow(() -> notFound("Category not found."));
    }

    private Product requireProduct(long id) {
        return products.findById(id).orElseThrow(() -> notFound("Product not found."));
    }

    private boolean hasActiveAncestry(Category category) {
        for (Category cursor = category; cursor != null; cursor = cursor.getParent()) if (!cursor.isActive()) return false;
        return true;
    }

    private void touch(long groupId) {
        jdbc.update("UPDATE navigation_groups SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", groupId);
    }

    private StorefrontSettingsDto settingsRow(ResultSet rs, int row) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new StorefrontSettingsDto(rs.getString("hero_image_url"), rs.getString("hero_image_alt_text"),
            rs.getString("hero_heading"), rs.getString("hero_supporting_text"), updated.toInstant());
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static StorefrontApiException badRequest(String message) { return new StorefrontApiException(HttpStatus.BAD_REQUEST, message); }
    private static StorefrontApiException notFound(String message) { return new StorefrontApiException(HttpStatus.NOT_FOUND, message); }
    private static StorefrontApiException conflict(String message) { return new StorefrontApiException(HttpStatus.CONFLICT, message); }
    private record GroupRow(long id, String label, boolean active, int displayOrder) {}
}
