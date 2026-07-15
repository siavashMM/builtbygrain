package com.builtbygrain.backend.catalog;

import static com.builtbygrain.backend.catalog.CatalogDtos.*;
import static com.builtbygrain.backend.catalog.CatalogAdminDtos.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import com.builtbygrain.backend.product.*;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CatalogManagementService management;
    private final JdbcTemplate jdbc;
    public CatalogService(CategoryRepository categories, ProductRepository products, ProductVariantRepository variants,
            CatalogManagementService management, JdbcTemplate jdbc) {
        this.categories = categories; this.products = products; this.variants = variants; this.management = management; this.jdbc = jdbc;
    }
    @Transactional(readOnly=true)
    public List<TreeNode> roots() { return categories.findByParentIsNullOrderBySortOrderAscNameAsc().stream().map(this::categoryNode).toList(); }
    @Transactional(readOnly=true)
    public List<CategoryResponse> allCategories() { return categories.findAll().stream()
        .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)).map(CategoryResponse::from).toList(); }
    @Transactional(readOnly=true)
    public List<CategoryResponse> activeCategories() { return categories.findAll().stream().filter(Category::isActive)
        .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
        .map(CategoryResponse::from).toList(); }
    @Transactional(readOnly=true)
    public CategoryResponse details(long id) { return CategoryResponse.from(category(id)); }
    @Transactional(readOnly=true)
    public List<TreeNode> children(String nodeId) {
        String[] parts = nodeId.split(":", 2);
        if (parts.length != 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog node id");
        long id = Long.parseLong(parts[1]);
        if (parts[0].equals("category")) {
            List<TreeNode> result = new ArrayList<>();
            categories.findByParentIdOrderBySortOrderAscNameAsc(id).stream().map(this::categoryNode).forEach(result::add);
            products.findByCategoryIdOrderByNameAsc(id).stream().map(this::productNode).forEach(result::add);
            return result;
        }
        if (parts[0].equals("product")) return management.variants(id).stream().map(this::variantNode).toList();
        return List.of();
    }
    @Transactional
    public Category create(CategoryRequest request) {
        Category parent = request.parentId() == null ? null : category(request.parentId());
        ensureSlug(request.slug(), parent, -1L);
        Category value = new Category(request.name().trim(), request.slug(), parent,
            request.sortOrder() == null ? nextSort(parent) : request.sortOrder());
        value.update(request.name().trim(), request.slug(), request.description(), request.imageUrl(), request.active());
        return categories.save(value);
    }
    @Transactional
    public Category update(long id, CategoryRequest request) {
        Category value = category(id);
        Category parent = request.parentId() == null ? null : category(request.parentId());
        assertValidParent(value, parent); ensureSlug(request.slug(), parent, id);
        value.update(request.name().trim(), request.slug(), request.description(), request.imageUrl(), request.active());
        value.moveTo(parent, request.sortOrder() == null ? value.getSortOrder() : request.sortOrder()); return value;
    }
    @Transactional
    public Category move(long id, MoveRequest request) {
        Category value = category(id); Category parent = request.parentId() == null ? null : category(request.parentId());
        assertValidParent(value, parent); ensureSlug(value.getSlug(), parent, id); value.moveTo(parent, request.sortOrder()); return value;
    }
    @Transactional
    public Category setStatus(long id, StatusRequest request) {
        Category value = category(id);
        value.update(value.getName(), value.getSlug(), value.getDescription(), value.getImageUrl(), request.active());
        return value;
    }
    @Transactional
    public void delete(long id, boolean confirmed) {
        Category value = category(id);
        boolean unsafe = !categories.findByParentIdOrderBySortOrderAscNameAsc(id).isEmpty() || products.existsByCategoryIdAndActiveTrue(id);
        if (unsafe || !confirmed) throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has children or active products; deactivate or confirm a safe reassignment first");
        categories.delete(value);
    }
    @Transactional
    public CatalogResetResponse reset(CatalogResetRequest request) {
        if (!"RESET CATALOG".equals(request.confirmation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type RESET CATALOG to confirm");
        }
        int deletedVariants = jdbc.update("DELETE FROM product_variants");
        int deletedProducts = jdbc.update("DELETE FROM products");
        int deletedCategories = jdbc.update("DELETE FROM categories");
        return new CatalogResetResponse(deletedProducts, deletedVariants, deletedCategories);
    }
    private Category category(long id) { return categories.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found")); }
    private TreeNode categoryNode(Category c) { long count=products.countByCategoryId(c.getId()); return new TreeNode("category:"+c.getId(), NodeType.CATEGORY,
        c.getParent()==null?null:"category:"+c.getParent().getId(), c.getName(), count+" product"+(count==1?"":"s"), c.isActive()?"ACTIVE":"INACTIVE", true, "folder", c.getSortOrder()); }
    private TreeNode productNode(Product p) { long count=management.variants(p.getId()).size(); return new TreeNode("product:"+p.getId(), NodeType.PRODUCT, "category:"+p.getCategory().getId(),
        p.getName(), p.getSlug()+" · "+count+" variant"+(count==1?"":"s"), p.getStatus().name(), count>0, "inventory_2", 0); }
    private TreeNode variantNode(VariantDto v) { return new TreeNode("variant:"+v.id(), NodeType.VARIANT, "product:"+v.productId(),
        v.label(), formatMoney(v.regularPriceCents())+" · "+v.stockQuantity()+" stock", v.active()?v.availabilityStatus():"INACTIVE", false, "tune", 0); }
    private String formatMoney(long cents){return String.format(java.util.Locale.ROOT,"€%.2f",cents/100.0);}
    private int nextSort(Category parent) { return parent == null ? categories.findByParentIsNullOrderBySortOrderAscNameAsc().size() : categories.findByParentIdOrderBySortOrderAscNameAsc(parent.getId()).size(); }
    private void ensureSlug(String slug, Category parent, long id) { boolean exists = parent == null ? categories.existsByParentIsNullAndSlugAndIdNot(slug,id) : categories.existsByParentIdAndSlugAndIdNot(parent.getId(),slug,id); if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT,"Category slug already exists at this level"); }
    private void assertValidParent(Category value, Category parent) { for (Category cursor=parent; cursor!=null; cursor=cursor.getParent()) if (cursor.getId().equals(value.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A category cannot be its own parent or descendant"); }
}
