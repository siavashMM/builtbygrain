package com.builtbygrain.backend.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryRequest;
import com.builtbygrain.backend.product.Product;
import com.builtbygrain.backend.product.ProductRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCatalogEndpointTest {
    @Autowired MockMvc mockMvc;
    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminCanUpdateAndDeleteCategory() throws Exception {
        Category category = catalog.create(new CategoryRequest(
            "Endpoint Test", "endpoint-test", null, null, null, 0, true
        ));

        mockMvc.perform(put("/api/admin/categories/{id}", category.getId())
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Updated Endpoint Test","slug":"updated-endpoint-test",
                     "parentId":null,"description":null,"imageUrl":null,
                     "sortOrder":0,"active":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Endpoint Test"));

        mockMvc.perform(delete("/api/admin/categories/{id}", category.getId())
                .queryParam("confirmed", "true")
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isNoContent());
    }

    @Test
    void onlyAdminCanCreateProductInsideCategory() throws Exception {
        Category category = catalog.create(new CategoryRequest(
            "Serving Boards", "serving-boards", null, null, null, 0, true
        ));
        String product = """
            {"name":"Oak Board","slug":"category-oak-board",
             "description":"Handmade oak board","priceCents":4900,
             "currency":"EUR","inStock":true,"sizes":[],"active":true}
            """;

        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", category.getId())
                .with(httpBasic("user", "password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(product))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", category.getId())
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(product))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Oak Board"))
            .andExpect(jsonPath("$.categoryId").value(category.getId()))
            .andExpect(jsonPath("$.categoryName").value("Serving Boards"));
    }

    @Test
    void onlyAdminCanResetCatalogWithExactConfirmation() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/reset")
                .with(httpBasic("user", "password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET CATALOG\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/catalog/reset")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"reset\"}"))
            .andExpect(status().isBadRequest());

        catalog.create(new CategoryRequest(
            "Reset Test", "reset-test", null, null, null, 0, true
        ));

        mockMvc.perform(post("/api/admin/catalog/reset")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET CATALOG\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoriesDeleted").isNumber())
            .andExpect(jsonPath("$.productsDeleted").isNumber())
            .andExpect(jsonPath("$.variantsDeleted").isNumber());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/catalog/tree")
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publicNavigationIncludesOnlyActiveBranchesContainingPublicProducts() throws Exception {
        Category root = catalog.create(new CategoryRequest("Public Shelves", "public-shelves", null, null, null, 0, true));
        Category child = catalog.create(new CategoryRequest("Floating", "floating", root.getId(), null, null, 0, true));
        catalog.create(new CategoryRequest("Hidden Shelves", "hidden-shelves", null, null, null, 1, false));
        catalog.create(new CategoryRequest("Empty Shelves", "empty-shelves", null, null, null, 2, true));

        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", child.getId())
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Public Product","slug":"public-navigation-product","description":null,
                     "priceCents":4900,"currency":"EUR","inStock":false,"sizes":[],"active":true}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/navigation/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("public-shelves"))
            .andExpect(jsonPath("$[0].children[0].slug").value("floating"))
            .andExpect(jsonPath("$[0].children[0].path").value("/category/public-shelves/floating"))
            .andExpect(jsonPath("$[?(@.slug == 'hidden-shelves')]").doesNotExist())
            .andExpect(jsonPath("$[?(@.slug == 'empty-shelves')]").doesNotExist());
    }

    @Test
    void publicCategoriesIncludeOnlyActiveRootsInConfiguredOrderWithCanonicalPaths() throws Exception {
        catalog.create(new CategoryRequest("Second Public Root", "second-public-root", null, "Second", null, 501, true));
        catalog.create(new CategoryRequest("First Public Root", "first-public-root", null, "First", "/first.jpg", 500, true));
        catalog.create(new CategoryRequest("Inactive Public Root", "inactive-public-root", null, null, null, 499, false));

        mockMvc.perform(get("/api/public/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'inactive-public-root')]").doesNotExist())
            .andExpect(jsonPath("$[?(@.slug == 'first-public-root')].path").value("/category/first-public-root"))
            .andExpect(jsonPath("$[?(@.slug == 'first-public-root')].description").value("First"))
            .andExpect(jsonPath("$[?(@.slug == 'first-public-root')].imageUrl").value("/first.jpg"));

        var roots = catalog.publicCategories().stream().filter(category -> category.parentId() == null)
            .filter(category -> category.slug().endsWith("public-root")).toList();
        org.assertj.core.api.Assertions.assertThat(roots).extracting(com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse::slug)
            .containsSubsequence("first-public-root", "second-public-root");
    }

    @Test
    void publicCategoryPageIncludesDirectAndActiveDescendantProductsForNestedPaths() throws Exception {
        Category office = catalog.create(new CategoryRequest("Endpoint Office", "endpoint-office", null, "Office pieces", null, 600, true));
        Category desks = catalog.create(new CategoryRequest("Endpoint Desks", "endpoint-desks", office.getId(), "Desks", null, 0, true));
        Category standing = catalog.create(new CategoryRequest("Endpoint Standing", "endpoint-standing", desks.getId(), null, null, 0, true));
        createPublicProduct(office, "Office Direct", "endpoint-office-direct");
        createPublicProduct(desks, "Desk Direct", "endpoint-desk-direct");
        createPublicProduct(standing, "Standing Direct", "endpoint-standing-direct");

        mockMvc.perform(get("/api/public/category").queryParam("path", "endpoint-office"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category.path").value("/category/endpoint-office"))
            .andExpect(jsonPath("$.products[?(@.slug == 'endpoint-office-direct')]").exists())
            .andExpect(jsonPath("$.products[?(@.slug == 'endpoint-desk-direct')]").exists())
            .andExpect(jsonPath("$.products[?(@.slug == 'endpoint-standing-direct')]").exists());

        mockMvc.perform(get("/api/public/category").queryParam("path", "endpoint-office/endpoint-desks/endpoint-standing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category.name").value("Endpoint Standing"))
            .andExpect(jsonPath("$.breadcrumbs.length()").value(3))
            .andExpect(jsonPath("$.breadcrumbs[1].path").value("/category/endpoint-office/endpoint-desks"))
            .andExpect(jsonPath("$.products.length()").value(1))
            .andExpect(jsonPath("$.products[0].slug").value("endpoint-standing-direct"));
    }

    private void createPublicProduct(Category category, String name, String slug) throws Exception {
        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", category.getId())
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","slug":"%s","description":null,
                     "priceCents":4900,"currency":"EUR","inStock":true,"sizes":[],"active":true}
                    """.formatted(name, slug)))
            .andExpect(status().isCreated());
    }

    @Test
    void adminCanDeleteCatalogImageById() throws Exception {
        Product product=products.save(new Product("Delete Endpoint Image", "delete-endpoint-image", "Test", 4900, "EUR", null));
        jdbc.update("INSERT INTO product_images(product_id,image_url,display_order,shared,active,created_at) VALUES(?,?,0,TRUE,TRUE,CURRENT_TIMESTAMP)",product.getId(),"/delete-endpoint.jpg");
        Long imageId=jdbc.queryForObject("SELECT id FROM product_images WHERE product_id=?",Long.class,product.getId());

        mockMvc.perform(delete("/api/admin/products/{productId}/catalog-images/{imageId}",product.getId(),imageId)
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isNoContent());
    }
}
