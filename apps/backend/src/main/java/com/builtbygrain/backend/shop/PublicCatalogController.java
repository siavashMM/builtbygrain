package com.builtbygrain.backend.shop;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.builtbygrain.backend.catalog.CatalogService;
import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;

@RestController
public class PublicCatalogController {
    private final CatalogService catalog;
    public PublicCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/api/public/categories")
    public List<CategoryResponse> categories() { return catalog.activeCategories(); }
}
