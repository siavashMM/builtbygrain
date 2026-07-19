package com.builtbygrain.backend.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    java.util.Optional<Category> findBySlugAndParentIsNull(String slug);
    java.util.Optional<Category> findBySlugAndParentId(String slug, Long parentId);
    List<Category> findByParentIsNullOrderBySortOrderAscNameAsc();
    List<Category> findByParentIdOrderBySortOrderAscNameAsc(Long parentId);
    boolean existsByParentIdAndSlugAndIdNot(Long parentId, String slug, Long id);
    boolean existsByParentIsNullAndSlugAndIdNot(String slug, Long id);
}
