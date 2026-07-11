package com.builtbygrain.backend.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findAllByOrderByNameAsc();

    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
