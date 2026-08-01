package com.builtbygrain.backend.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryRequest;
import com.builtbygrain.backend.catalog.CatalogDtos.CategoryResponse;
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
        CategoryResponse category = catalog.create(new CategoryRequest(
            "Endpoint Test", "endpoint-test", null, null, null, 0, true
        ));

        mockMvc.perform(put("/api/admin/categories/{id}", category.id())
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Updated Endpoint Test","slug":"updated-endpoint-test",
                     "parentId":null,"description":null,"imageUrl":null,
                     "sortOrder":0,"active":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Endpoint Test"));

        mockMvc.perform(delete("/api/admin/categories/{id}", category.id())
                .queryParam("confirmed", "true")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void onlyAdminCanCreateProductInsideCategory() throws Exception {
        CategoryResponse category = catalog.create(new CategoryRequest(
            "Serving Boards", "serving-boards", null, null, null, 0, true
        ));
        String product = """
            {"name":"Oak Board","slug":"category-oak-board",
             "description":"Handmade oak board","priceCents":4900,
             "currency":"EUR","inStock":true,"sizes":[],"active":true}
            """;

        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", category.id())
                .with(user("user").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(product))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", category.id())
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(product))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Oak Board"))
            .andExpect(jsonPath("$.categoryId").value(category.id()))
            .andExpect(jsonPath("$.categoryName").value("Serving Boards"));
    }

    @Test
    void catalogResetEndpointHasBeenRemoved() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/reset")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET CATALOG\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void creatingThirdLevelCategoryReturnsSuccessAfterItsTransactionCloses() throws Exception {
        long rootId = createSimpleCategory("Response Root", null);
        long childId = createSimpleCategory("Response Child", rootId);

        mockMvc.perform(post("/api/admin/categories").queryParam("simple", "true")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Response Grandchild","parentId":%d}
                    """.formatted(childId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parentId").value(childId))
            .andExpect(jsonPath("$.path").value("/category/response-root/response-child/response-grandchild"));

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM categories WHERE parent_id=? AND name=?",
            Integer.class, childId, "Response Grandchild"
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void publicNavigationIncludesOnlyActiveBranchesContainingPublicProducts() throws Exception {
        CategoryResponse root = catalog.create(new CategoryRequest("Public Shelves", "public-shelves", null, null, null, 0, true));
        CategoryResponse child = catalog.create(new CategoryRequest("Floating", "floating", root.id(), null, null, 0, true));
        catalog.create(new CategoryRequest("Hidden Shelves", "hidden-shelves", null, null, null, 1, false));
        catalog.create(new CategoryRequest("Empty Shelves", "empty-shelves", null, null, null, 2, true));

        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", child.id())
                .with(user("admin").roles("ADMIN")).with(csrf())
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
        CategoryResponse office = catalog.create(new CategoryRequest("Endpoint Office", "endpoint-office", null, "Office pieces", null, 600, true));
        CategoryResponse desks = catalog.create(new CategoryRequest("Endpoint Desks", "endpoint-desks", office.id(), "Desks", null, 0, true));
        CategoryResponse standing = catalog.create(new CategoryRequest("Endpoint Standing", "endpoint-standing", desks.id(), null, null, 0, true));
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

    private long createSimpleCategory(String name, Long parentId) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        String body = mockMvc.perform(post("/api/admin/categories").queryParam("simple", "true")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","parentId":%s}
                    """.formatted(name, parent)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return new tools.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();
    }

    private void createPublicProduct(CategoryResponse category, String name, String slug) throws Exception {
        mockMvc.perform(post("/api/admin/categories/{categoryId}/products", category.id())
                .with(user("admin").roles("ADMIN")).with(csrf())
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
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
    }
}
