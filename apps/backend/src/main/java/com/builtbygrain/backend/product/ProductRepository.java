package com.builtbygrain.backend.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findAllByOrderByNameAsc();
    List<Product> findByCategoryIdOrderByNameAsc(Long categoryId);
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
    long countByCategoryId(Long categoryId);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
