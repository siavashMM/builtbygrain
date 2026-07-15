package com.builtbygrain.backend.catalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);
    Optional<ProductVariant> findByProductIdAndPublicId(Long productId, String publicId);
    long countByProductId(Long productId);
}
