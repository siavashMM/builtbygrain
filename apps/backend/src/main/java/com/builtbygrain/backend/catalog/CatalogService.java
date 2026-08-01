package com.builtbygrain.backend.catalog;

import static com.builtbygrain.backend.catalog.CatalogDtos.*;
import static com.builtbygrain.backend.catalog.CatalogAdminDtos.*;
import java.util.*;
import java.text.Normalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.builtbygrain.backend.product.*;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductCardService productCards;
    private final JdbcTemplate jdbc;
    public CatalogService(CategoryRepository categories, ProductRepository products, ProductCardService productCards,
        JdbcTemplate jdbc) {
        this.categories = categories; this.products = products; this.productCards = productCards; this.jdbc = jdbc;
    }
    @Transactional(readOnly=true)
    public List<TreeNode> roots() {
        Map<Long, Long> productCounts = directProductCounts();
        Set<Long> parents = categoryParents();
        return categories.findByParentIsNullOrderBySortOrderAscNameAsc().stream()
            .map(category -> categoryNode(category, productCounts, parents)).toList();
    }
    @Transactional(readOnly=true)
    public List<CategoryResponse> allCategories() { return categories.findAll().stream()
        .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)).map(CategoryResponse::from).toList(); }
    @Transactional(readOnly=true)
    public CategoryResponse details(long id) { return CategoryResponse.from(category(id)); }
    @Transactional(readOnly=true)
    public List<TreeNode> children(String nodeId) {
        String[] parts = nodeId.split(":", 2);
        if (parts.length != 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog node id");
        long id = Long.parseLong(parts[1]);
        if (parts[0].equals("category")) {
            Map<Long, Long> productCounts = directProductCounts();
            Set<Long> parents = categoryParents();
            return categories.findByParentIdOrderBySortOrderAscNameAsc(id).stream()
                .map(category -> categoryNode(category, productCounts, parents)).toList();
        }
        return List.of();
    }
    @Transactional(readOnly=true)
    public List<AdminCategoryNode> adminTree() {
        List<Category> all = sortedCategories();
        Map<Long, List<Category>> children = groupChildren(all);
        Map<Long, Long> productCounts = directProductCounts();
        return children.getOrDefault(null, List.of()).stream()
            .map(category -> adminNode(category, children, productCounts)).toList();
    }
    @Transactional(readOnly=true)
    public List<CategoryOption> options() {
        return sortedCategories().stream().map(category -> {
            List<String> names = new ArrayList<>();
            for (Category cursor = category; cursor != null; cursor = cursor.getParent()) names.add(cursor.getName());
            Collections.reverse(names);
            return new CategoryOption(category.getId(), category.getName(), String.join(" / ", names), category.isActive(), names.size() - 1);
        }).sorted(Comparator.comparing(CategoryOption::path, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    @Transactional(readOnly=true)
    public List<NavigationCategory> navigation() {
        List<Category> all = sortedCategories();
        Map<Long, List<Category>> children = groupChildren(all);
        Set<Long> productCategories = new HashSet<>(products.findActiveCategoryIds());
        return children.getOrDefault(null, List.of()).stream()
            .map(category -> navigationNode(category, children, productCategories, ""))
            .flatMap(Optional::stream).toList();
    }
    @Transactional(readOnly=true)
    public List<CategoryResponse> publicCategories() {
        List<Category> all = sortedCategories();
        Map<Long, List<Category>> children = groupChildren(all);
        List<CategoryResponse> result = new ArrayList<>();
        for (Category root : children.getOrDefault(null, List.of())) collectActiveCategories(root, children, result);
        return result;
    }
    @Transactional(readOnly=true)
    public CategoryPageResponse publicCategoryPage(String rawPath) {
        String path = rawPath == null ? "" : rawPath.strip().replaceAll("^/+|/+$", "");
        if (path.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        String[] slugs = path.split("/");
        Category current = categories.findBySlugAndParentIsNull(slugs[0])
            .filter(Category::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        List<CategoryResponse> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(CategoryResponse.from(current));
        for (int index = 1; index < slugs.length; index++) {
            current = categories.findBySlugAndParentId(slugs[index], current.getId())
                .filter(Category::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            breadcrumbs.add(CategoryResponse.from(current));
        }
        Category selected = current;
        List<Category> all = sortedCategories();
        Map<Long, List<Category>> children = groupChildren(all);
        Set<Long> categoryIds = new HashSet<>();
        collectActiveDescendantIds(selected, children, categoryIds);
        List<ProductCardDto> matchingProducts = productCards.activeCardsForCategoryIds(categoryIds);
        return new CategoryPageResponse(CategoryResponse.from(selected), breadcrumbs, matchingProducts);
    }
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        Category parent = request.parentId() == null ? null : category(request.parentId());
        String slug = uniqueSlug(request.name(), parent, -1L);
        return CategoryResponse.from(categories.save(new Category(request.name().trim(), slug, parent, nextSort(parent))));
    }
    @Transactional
    public CategoryResponse rename(long id, CategoryNameRequest request) {
        Category value = category(id);
        value.rename(request.name().trim());
        return CategoryResponse.from(value);
    }
    @Transactional
    public CategoryResponse move(long id, CategoryParentRequest request) {
        Category value = category(id);
        Category parent = request.parentId() == null ? null : category(request.parentId());
        assertValidParent(value, parent);
        ensureSlug(value.getSlug(), parent, id);
        value.moveTo(parent, request.position() == null ? nextSort(parent) : request.position());
        normalizeSiblings(parent);
        return CategoryResponse.from(value);
    }
    @Transactional
    public CategoryResponse position(long id, CategoryPositionRequest request) {
        Category value = category(id);
        List<Category> siblings = siblings(value.getParent()).stream().filter(item -> !item.getId().equals(id)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        siblings.add(Math.min(request.position(), siblings.size()), value);
        for (int index = 0; index < siblings.size(); index++) siblings.get(index).moveTo(value.getParent(), index);
        return CategoryResponse.from(value);
    }
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category parent = request.parentId() == null ? null : category(request.parentId());
        ensureSlug(request.slug(), parent, -1L);
        Category value = new Category(request.name().trim(), request.slug(), parent,
            request.sortOrder() == null ? nextSort(parent) : request.sortOrder());
        value.update(request.name().trim(), request.slug(), request.description(), request.imageUrl(), request.active());
        return CategoryResponse.from(categories.save(value));
    }
    @Transactional
    public CategoryResponse update(long id, CategoryRequest request) {
        Category value = category(id);
        Category parent = request.parentId() == null ? null : category(request.parentId());
        assertValidParent(value, parent); ensureSlug(request.slug(), parent, id);
        value.update(request.name().trim(), request.slug(), request.description(), request.imageUrl(), request.active());
        value.moveTo(parent, request.sortOrder() == null ? value.getSortOrder() : request.sortOrder());
        return CategoryResponse.from(value);
    }
    @Transactional
    public CategoryResponse move(long id, MoveRequest request) {
        Category value = category(id); Category parent = request.parentId() == null ? null : category(request.parentId());
        assertValidParent(value, parent); ensureSlug(value.getSlug(), parent, id); value.moveTo(parent, request.sortOrder());
        return CategoryResponse.from(value);
    }
    @Transactional
    public CategoryResponse setStatus(long id, StatusRequest request) {
        Category value = category(id);
        value.setActive(request.active());
        return CategoryResponse.from(value);
    }
    @Transactional
    public void delete(long id, boolean confirmed) {
        Category value = category(id);
        int childCount = categories.findByParentIdOrderBySortOrderAscNameAsc(id).size();
        long productCount = products.countByCategoryId(id);
        if (childCount > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This category cannot be deleted because it contains " + childCount + " child categories.");
        if (productCount > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This category cannot be deleted because " + productCount + " products are assigned to it. Move those products first.");
        categories.delete(value);
    }
    private Category category(long id) { return categories.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found")); }
    private TreeNode categoryNode(Category c, Map<Long, Long> productCounts, Set<Long> parents) { long count=productCounts.getOrDefault(c.getId(), 0L); boolean hasChildren=parents.contains(c.getId()); return new TreeNode("category:"+c.getId(), NodeType.CATEGORY,
        c.getParent()==null?null:"category:"+c.getParent().getId(), c.getName(), count+" product"+(count==1?"":"s"), c.isActive()?"ACTIVE":"INACTIVE", hasChildren, "folder", c.getSortOrder()); }
    private int nextSort(Category parent) { return parent == null ? categories.findByParentIsNullOrderBySortOrderAscNameAsc().size() : categories.findByParentIdOrderBySortOrderAscNameAsc(parent.getId()).size(); }
    private void ensureSlug(String slug, Category parent, long id) { boolean exists = parent == null ? categories.existsByParentIsNullAndSlugAndIdNot(slug,id) : categories.existsByParentIdAndSlugAndIdNot(parent.getId(),slug,id); if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT,"Category slug already exists at this level"); }
    private void assertValidParent(Category value, Category parent) { for (Category cursor=parent; cursor!=null; cursor=cursor.getParent()) if (cursor.getId().equals(value.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A category cannot be its own parent or descendant"); }
    private List<Category> sortedCategories() { return categories.findAll().stream().sorted(Comparator.comparing((Category c) -> c.getParent() == null ? -1L : c.getParent().getId()).thenComparingInt(Category::getSortOrder).thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER)).toList(); }
    private Map<Long,List<Category>> groupChildren(List<Category> all) { Map<Long,List<Category>> result = new HashMap<>(); for (Category category : all) result.computeIfAbsent(category.getParent() == null ? null : category.getParent().getId(), ignored -> new ArrayList<>()).add(category); return result; }
    private AdminCategoryNode adminNode(Category category, Map<Long,List<Category>> children, Map<Long, Long> productCounts) { return new AdminCategoryNode(category.getId(), category.getParent()==null?null:category.getParent().getId(), category.getName(), category.getSlug(), category.isActive(), category.getSortOrder(), productCounts.getOrDefault(category.getId(), 0L), children.getOrDefault(category.getId(),List.of()).stream().map(child -> adminNode(child, children, productCounts)).toList()); }

    private Map<Long, Long> directProductCounts() {
        return jdbc.query("SELECT category_id, COUNT(*) product_count FROM products GROUP BY category_id", rs -> {
            Map<Long, Long> counts = new HashMap<>();
            while (rs.next()) counts.put(rs.getLong("category_id"), rs.getLong("product_count"));
            return Map.copyOf(counts);
        });
    }

    private Set<Long> categoryParents() {
        return Set.copyOf(jdbc.queryForList("SELECT DISTINCT parent_id FROM categories WHERE parent_id IS NOT NULL", Long.class));
    }
    private Optional<NavigationCategory> navigationNode(Category category, Map<Long,List<Category>> children, Set<Long> productCategories, String parentPath) {
        if (!category.isActive()) return Optional.empty();
        String path = parentPath + "/" + category.getSlug();
        List<NavigationCategory> visibleChildren = children.getOrDefault(category.getId(), List.of()).stream().map(child -> navigationNode(child, children, productCategories, path)).flatMap(Optional::stream).toList();
        if (!productCategories.contains(category.getId()) && visibleChildren.isEmpty()) return Optional.empty();
        return Optional.of(new NavigationCategory(category.getId(), category.getName(), category.getSlug(), "/category" + path, visibleChildren));
    }
    private void collectActiveCategories(Category category, Map<Long,List<Category>> children, List<CategoryResponse> result) {
        if (!category.isActive()) return;
        result.add(CategoryResponse.from(category));
        for (Category child : children.getOrDefault(category.getId(), List.of())) collectActiveCategories(child, children, result);
    }
    private void collectActiveDescendantIds(Category category, Map<Long,List<Category>> children, Set<Long> ids) {
        if (!category.isActive()) return;
        ids.add(category.getId());
        for (Category child : children.getOrDefault(category.getId(), List.of())) collectActiveDescendantIds(child, children, ids);
    }
    private String uniqueSlug(String name, Category parent, long id) { String base = slugify(name); String candidate = base; int suffix = 2; while (slugExists(candidate, parent, id)) candidate = base + "-" + suffix++; return candidate; }
    private String slugify(String value) { String slug = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); return slug.isBlank() ? "category" : slug; }
    private boolean slugExists(String slug, Category parent, long id) { return parent == null ? categories.existsByParentIsNullAndSlugAndIdNot(slug,id) : categories.existsByParentIdAndSlugAndIdNot(parent.getId(),slug,id); }
    private List<Category> siblings(Category parent) { return parent == null ? categories.findByParentIsNullOrderBySortOrderAscNameAsc() : categories.findByParentIdOrderBySortOrderAscNameAsc(parent.getId()); }
    private void normalizeSiblings(Category parent) { List<Category> siblings = siblings(parent); for (int index=0; index<siblings.size(); index++) siblings.get(index).moveTo(parent,index); }
}
