package com.builtbygrain.backend.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByNameAsc();
    List<Product> findByCategoryIdOrderByNameAsc(Long categoryId);
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
    long countByCategoryId(Long categoryId);
    @Query("select distinct p.category.id from Product p where p.active = true and p.status = 'ACTIVE'")
    List<Long> findActiveCategoryIds();

    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
