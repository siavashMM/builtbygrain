package com.builtbygrain.backend.shop;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.builtbygrain.backend.catalog.CatalogService;
import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;
import com.builtbygrain.backend.catalog.CatalogDtos.NavigationCategory;
import com.builtbygrain.backend.catalog.CatalogDtos.CategoryPageResponse;

@RestController
public class PublicCatalogController {
    private final CatalogService catalog;
    public PublicCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/api/public/categories")
    public List<CategoryResponse> categories() { return catalog.publicCategories(); }

    @GetMapping("/api/public/category")
    public CategoryPageResponse category(@RequestParam String path) { return catalog.publicCategoryPage(path); }

    @GetMapping("/api/navigation/categories")
    public List<NavigationCategory> navigation() { return catalog.navigation(); }
}
