package com.builtbygrain.backend.storefront;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryRequest;
import com.builtbygrain.backend.catalog.CatalogService;
import com.builtbygrain.backend.product.Product;
import com.builtbygrain.backend.product.ProductRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontEndpointTest {
    @Autowired MockMvc mockMvc;
    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;
    @Autowired StorefrontImageStorageService storefrontImages;

    @Test
    void adminCanManageSettingsAndHeroImageWhilePublicCanReadAggregate() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/settings")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"heroImageAltText":null,"heroHeading":"New heading","heroSupportingText":"New support"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.heroHeading").value("New heading"));

        MockMultipartFile image = new MockMultipartFile("image", "hero.png", MediaType.IMAGE_PNG_VALUE, "image".getBytes());
        String firstUpload = mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(image).param("altText", "Oak shelves")
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.heroImageUrl").value(org.hamcrest.Matchers.startsWith("/api/public/uploads/storefront/")))
            .andExpect(jsonPath("$.heroImageAltText").value("Oak shelves"))
            .andReturn().getResponse().getContentAsString();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String firstUrl = mapper.readTree(firstUpload).get("heroImageUrl").asText();
        mockMvc.perform(get(firstUrl)).andExpect(status().isOk());

        MockMultipartFile replacement = new MockMultipartFile("image", "replacement.jpg", MediaType.IMAGE_JPEG_VALUE, "replacement".getBytes());
        String secondUpload = mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(replacement).param("altText", "Replacement hero")
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String replacementUrl = mapper.readTree(secondUpload).get("heroImageUrl").asText();
        mockMvc.perform(get(firstUrl)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/public/storefront"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settings.heroHeading").value("New heading"))
            .andExpect(jsonPath("$.navigationGroups").isArray());
        storefrontImages.delete(replacementUrl);
    }

    @Test
    void adminCanManageAndOrderGroupAssignments() throws Exception {
        var firstCategory = catalog.create(new CategoryRequest("Endpoint First", "endpoint-config-first", null, null, null, 0, true));
        var secondCategory = catalog.create(new CategoryRequest("Endpoint Second", "endpoint-config-second", null, null, null, 1, true));
        Product firstProduct = products.save(new Product("Endpoint Featured 1", "endpoint-config-product-1", null, 1200, "EUR", null));
        Product secondProduct = products.save(new Product("Endpoint Featured 2", "endpoint-config-product-2", null, 1300, "EUR", null));

        String body = mockMvc.perform(post("/api/admin/storefront/navigation-groups")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Furniture\",\"active\":false}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long groupId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();

        assign(groupId, "categories", firstCategory.getId());
        assign(groupId, "categories", secondCategory.getId());
        assign(groupId, "featured-products", firstProduct.getId());
        assign(groupId, "featured-products", secondProduct.getId());

        mockMvc.perform(put("/api/admin/storefront/navigation-groups/{id}/categories/reorder", groupId)
                .with(httpBasic("admin", "admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[%d,%d]}".formatted(secondCategory.getId(), firstCategory.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].id").value(secondCategory.getId()));
        mockMvc.perform(put("/api/admin/storefront/navigation-groups/{id}/featured-products/reorder", groupId)
                .with(httpBasic("admin", "admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[%d,%d]}".formatted(secondProduct.getId(), firstProduct.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.featuredProducts[0].id").value(secondProduct.getId()));

        mockMvc.perform(patch("/api/admin/storefront/navigation-groups/{id}/activate", groupId)
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/public/storefront"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.navigationGroups[0].label").value("Furniture"));

        mockMvc.perform(delete("/api/admin/storefront/navigation-groups/{id}/categories/{categoryId}", groupId, firstCategory.getId())
                .with(httpBasic("admin", "admin"))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/storefront/navigation-groups/{id}/featured-products/{productId}", groupId, firstProduct.getId())
                .with(httpBasic("admin", "admin"))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/storefront/navigation-groups/{id}", groupId)
                .with(httpBasic("admin", "admin"))).andExpect(status().isNoContent());
    }

    @Test
    void validationReturnsUsefulClientErrors() throws Exception {
        mockMvc.perform(post("/api/admin/storefront/navigation-groups")
                .with(httpBasic("admin", "admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"\",\"active\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());

        mockMvc.perform(put("/api/admin/storefront/navigation-groups/999999")
                .with(httpBasic("admin", "admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Missing\",\"active\":true}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Navigation group not found."));

        MockMultipartFile text = new MockMultipartFile("image", "hero.txt", MediaType.TEXT_PLAIN_VALUE, "bad".getBytes());
        mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(text).param("altText", "Hero").with(httpBasic("admin", "admin")))
            .andExpect(status().isBadRequest());
    }

    private void assign(long groupId, String resource, long id) throws Exception {
        mockMvc.perform(post("/api/admin/storefront/navigation-groups/{id}/" + resource, groupId)
                .with(httpBasic("admin", "admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + id + "}"))
            .andExpect(status().isOk());
    }
}
