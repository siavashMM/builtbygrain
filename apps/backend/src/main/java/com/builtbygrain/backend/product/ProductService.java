package com.builtbygrain.backend.product;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.builtbygrain.backend.performance.PublicCacheNames;
import com.builtbygrain.backend.performance.ReadFromReplica;

@Service
@CacheEvict(
    cacheNames = {
        PublicCacheNames.PRODUCT_CARDS,
        PublicCacheNames.PRODUCT_DETAILS,
        PublicCacheNames.CATALOG,
        PublicCacheNames.STOREFRONT
    },
    allEntries = true,
    condition = "@publicCacheInvalidationPolicy.isMutation(#root.method)"
)
public class ProductService {

    private static final int MAX_IMAGES_PER_PRODUCT = 8;

    private final ProductRepository productRepository;
    private final ProductImageStorageService productImageStorageService;
    private final com.builtbygrain.backend.catalog.CategoryRepository categoryRepository;
    private final com.builtbygrain.backend.catalog.CatalogManagementService catalogManagement;
    private final JdbcTemplate jdbc;

    public ProductService(ProductRepository productRepository, ProductImageStorageService productImageStorageService,
            com.builtbygrain.backend.catalog.CategoryRepository categoryRepository,
            com.builtbygrain.backend.catalog.CatalogManagementService catalogManagement,
            JdbcTemplate jdbc) {
        this.productRepository = productRepository;
        this.productImageStorageService = productImageStorageService;
        this.categoryRepository = categoryRepository;
        this.catalogManagement = catalogManagement;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProductsForAdmin() {
        return productRepository.findAllByOrderByNameAsc()
            .stream()
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    @ReadFromReplica
    @Cacheable(cacheNames = PublicCacheNames.PRODUCT_DETAILS, key = "#slug", sync = true)
    public ProductResponse getActiveProductBySlug(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
            .map(this::response)
            .orElseThrow(() -> new ProductNotFoundException(slug));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        return createProduct(request, request.categoryId());
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, Long categoryId) {
        ensureSlugIsAvailable(request.slug());

        Product product = new Product(
            request.name(),
            request.slug(),
            request.description(),
            request.priceCents(),
            request.currency(),
            null
        );
        product.assignCategory(resolveCategory(categoryId));
        product.updateFrom(request);

        return response(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        ensureSlugIsAvailableForProduct(request.slug(), id);
        product.updateFrom(request);
        if (request.categoryId() != null) product.assignCategory(resolveCategory(request.categoryId()));
        return response(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductForAdmin(Long id) { return response(findProduct(id)); }

    @Transactional
    public ProductResponse moveProduct(Long id, Long categoryId) {
        Product product = findProduct(id); product.assignCategory(resolveCategory(categoryId)); return response(product);
    }

    @Transactional
    public ProductResponse duplicateProduct(Long id) {
        Product source = findProduct(id);
        String base = source.getSlug() + "-copy"; String slug = base; int suffix = 2;
        while (productRepository.existsBySlug(slug)) slug = base + "-" + suffix++;
        Product copy = new Product(source.getName() + " copy", slug, source.getDescription(), source.getPriceCents(), source.getCurrency(), null);
        copy.assignCategory(source.getCategory());
        return response(productRepository.save(copy));
    }

    @Transactional
    public ProductResponse deactivateProduct(Long id) {
        Product product = findProduct(id);
        product.deactivate();
        return response(product);
    }

    @Transactional
    public ProductResponse activateProduct(Long id) {
        Product product = findProduct(id);
        product.activate();
        return response(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        List<String> imageUrls = jdbc.query(
            "SELECT image_url FROM product_images WHERE product_id=?",
            (rs, row) -> rs.getString(1),
            id
        );
        jdbc.update("UPDATE products SET listing_primary_image_id=NULL,listing_hover_image_id=NULL WHERE id=?", id);
        productRepository.delete(product);
        productRepository.flush();
        imageUrls.forEach(productImageStorageService::delete);
    }

    @Transactional
    public ProductResponse addProductImages(Long id, List<org.springframework.web.multipart.MultipartFile> images) {
        Product product = findProduct(id);
        if (images == null || images.isEmpty()) {
            throw new ProductImageStorageException("Select at least one product image.");
        }
        if (product.getImageUrls().size() + images.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new ProductImageStorageException("A product can have at most 8 images.");
        }
        images.forEach(image -> product.addImageUrl(productImageStorageService.store(image)));
        return response(product);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, List<org.springframework.web.multipart.MultipartFile> images) {
        ProductResponse created = createProduct(request);
        if (images == null || images.isEmpty()) {
            return created;
        }
        return addProductImages(created.id(), images);
    }

    @Transactional
    public ProductResponse removeProductImage(Long id, int imageIndex) {
        Product product = findProduct(id);
        if (imageIndex < 0 || imageIndex >= product.getImageUrls().size()) {
            throw new ProductNotFoundException("image " + imageIndex);
        }
        Long imageId = jdbc.query("SELECT id FROM product_images WHERE product_id=? ORDER BY display_order,id OFFSET ? ROWS FETCH NEXT 1 ROWS ONLY",
            (rs, row) -> rs.getLong(1), id, imageIndex).stream().findFirst().orElse(null);
        if (imageId != null) {
            Integer listingUse = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND (listing_primary_image_id=? OR listing_hover_image_id=?)",
                Integer.class, id, imageId, imageId);
            if (listingUse != null && listingUse > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Clear or reassign this product card image before deleting it.");
            }
        }
        productImageStorageService.delete(product.removeImage(imageIndex));
        return response(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private ProductResponse response(Product product) {
        return ProductResponse.from(product, catalogManagement.configuration(product.getId()));
    }

    private com.builtbygrain.backend.catalog.Category resolveCategory(Long id) {
        if (id != null) return categoryRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Category not found"));
        return categoryRepository.findBySlugAndParentIsNull("uncategorized").orElseThrow(() -> new IllegalStateException("Default catalog category is missing"));
    }

    private void ensureSlugIsAvailable(String slug) {
        if (productRepository.existsBySlug(slug)) {
            throw new ProductSlugAlreadyExistsException(slug);
        }
    }

    private void ensureSlugIsAvailableForProduct(String slug, Long productId) {
        if (productRepository.existsBySlugAndIdNot(slug, productId)) {
            throw new ProductSlugAlreadyExistsException(slug);
        }
    }
}
