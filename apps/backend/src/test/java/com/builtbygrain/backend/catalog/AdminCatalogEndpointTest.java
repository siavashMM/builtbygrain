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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCatalogEndpointTest {
    @Autowired MockMvc mockMvc;
    @Autowired CatalogService catalog;

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
    void publicCatalogOnlyListsActiveCategories() throws Exception {
        catalog.create(new CategoryRequest("Public Shelves", "public-shelves", null, null, null, 0, true));
        catalog.create(new CategoryRequest("Hidden Shelves", "hidden-shelves", null, null, null, 1, false));

        mockMvc.perform(get("/api/public/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'public-shelves')]").exists())
            .andExpect(jsonPath("$[?(@.slug == 'hidden-shelves')]").doesNotExist());
    }
}
