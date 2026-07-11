package com.builtbygrain.backend.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final int MAX_IMAGES_PER_PRODUCT = 8;

    private final ProductRepository productRepository;
    private final ProductImageStorageService productImageStorageService;

    public ProductService(ProductRepository productRepository, ProductImageStorageService productImageStorageService) {
        this.productRepository = productRepository;
        this.productImageStorageService = productImageStorageService;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts() {
        return productRepository.findByActiveTrueOrderByNameAsc()
            .stream()
            .map(ProductResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProductsForAdmin() {
        return productRepository.findAllByOrderByNameAsc()
            .stream()
            .map(ProductResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveProductBySlug(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
            .map(ProductResponse::from)
            .orElseThrow(() -> new ProductNotFoundException(slug));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        ensureSlugIsAvailable(request.slug());

        Product product = new Product(
            request.name(),
            request.slug(),
            request.description(),
            request.priceCents(),
            request.currency(),
            null
        );
        product.updateFrom(request);

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        ensureSlugIsAvailableForProduct(request.slug(), id);
        product.updateFrom(request);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse deactivateProduct(Long id) {
        Product product = findProduct(id);
        product.deactivate();
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse activateProduct(Long id) {
        Product product = findProduct(id);
        product.activate();
        return ProductResponse.from(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        productRepository.delete(product);
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
        return ProductResponse.from(product);
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
        productImageStorageService.delete(product.removeImage(imageIndex));
        return ProductResponse.from(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
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
