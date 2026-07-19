package com.builtbygrain.backend.admin;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.builtbygrain.backend.catalog.*;
import com.builtbygrain.backend.catalog.CatalogDtos.*;
import com.builtbygrain.backend.catalog.CatalogAdminDtos.*;

@RestController @RequestMapping("/api/admin")
public class AdminCatalogController {
    private final CatalogService catalog;
    public AdminCatalogController(CatalogService catalog) { this.catalog = catalog; }
    @GetMapping("/catalog/tree") public List<TreeNode> tree() { return catalog.roots(); }
    @GetMapping("/categories/tree") public List<AdminCategoryNode> categoryTree() { return catalog.adminTree(); }
    @GetMapping("/categories/options") public List<CategoryOption> options() { return catalog.options(); }
    @GetMapping("/catalog/nodes/{nodeId}/children") public List<TreeNode> children(@PathVariable String nodeId) { return catalog.children(nodeId); }
    @GetMapping("/categories") public List<CategoryResponse> categories() { return catalog.allCategories(); }
    @GetMapping("/categories/{id}") public CategoryResponse category(@PathVariable long id) { return catalog.details(id); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) public CategoryResponse create(@Valid @RequestBody CategoryRequest request) { return CategoryResponse.from(catalog.create(request)); }
    @PostMapping(value="/categories", params="simple") @ResponseStatus(HttpStatus.CREATED) public CategoryResponse createSimple(@Valid @RequestBody CategoryCreateRequest request) { return CategoryResponse.from(catalog.create(request)); }
    @PatchMapping("/categories/{id}/name") public CategoryResponse rename(@PathVariable long id, @Valid @RequestBody CategoryNameRequest request) { return CategoryResponse.from(catalog.rename(id,request)); }
    @PatchMapping("/categories/{id}/parent") public CategoryResponse parent(@PathVariable long id, @Valid @RequestBody CategoryParentRequest request) { return CategoryResponse.from(catalog.move(id,request)); }
    @PatchMapping("/categories/{id}/position") public CategoryResponse position(@PathVariable long id, @Valid @RequestBody CategoryPositionRequest request) { return CategoryResponse.from(catalog.position(id,request)); }
    @PutMapping("/categories/{id}") public CategoryResponse update(@PathVariable long id, @Valid @RequestBody CategoryRequest request) { return CategoryResponse.from(catalog.update(id,request)); }
    @PatchMapping("/categories/{id}/move") public CategoryResponse move(@PathVariable long id, @Valid @RequestBody MoveRequest request) { return CategoryResponse.from(catalog.move(id,request)); }
    @PatchMapping("/categories/{id}/status") public CategoryResponse status(@PathVariable long id, @Valid @RequestBody StatusRequest request) { return CategoryResponse.from(catalog.setStatus(id,request)); }
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id, @RequestParam(defaultValue="false") boolean confirmed) { catalog.delete(id,confirmed); }
    @PostMapping("/catalog/reset") public CatalogResetResponse reset(@Valid @RequestBody CatalogResetRequest request) { return catalog.reset(request); }
}
