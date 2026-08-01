package com.builtbygrain.backend.storefront;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.builtbygrain.backend.catalog.CatalogDtos.CategoryRequest;
import com.builtbygrain.backend.catalog.CatalogService;
import com.builtbygrain.backend.product.Product;
import com.builtbygrain.backend.product.ProductRepository;
import com.builtbygrain.backend.product.TestImages;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontEndpointTest {
    @Autowired MockMvc mockMvc;
    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;
    @Autowired StorefrontImageStorageService storefrontImages;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearCommittedHeroReference() {
        jdbc.update("UPDATE storefront_settings SET hero_image_url=NULL, hero_image_alt_text=NULL WHERE id=1");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void adminCanManageSettingsAndHeroImageWhilePublicCanReadAggregate() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/settings")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"heroImageAltText":null,"heroHeading":"New heading","heroSupportingText":"New support"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.heroHeading").value("New heading"));

        MockMultipartFile image = new MockMultipartFile("image", "hero.png", MediaType.IMAGE_PNG_VALUE, TestImages.png());
        String firstUpload = mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(image).param("altText", "Oak shelves")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.heroImageUrl").value(org.hamcrest.Matchers.startsWith("/api/public/uploads/storefront/")))
            .andExpect(jsonPath("$.heroImageAltText").value("Oak shelves"))
            .andReturn().getResponse().getContentAsString();
        var mapper = new tools.jackson.databind.ObjectMapper();
        String firstUrl = mapper.readTree(firstUpload).get("heroImageUrl").asText();
        mockMvc.perform(get(firstUrl)).andExpect(status().isOk());

        MockMultipartFile replacement = new MockMultipartFile("image", "replacement.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg());
        String secondUpload = mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(replacement).param("altText", "Replacement hero")
                .with(user("admin").roles("ADMIN")).with(csrf()))
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
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Furniture\",\"active\":false}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long groupId = new tools.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();

        assign(groupId, "categories", firstCategory.id());
        assign(groupId, "categories", secondCategory.id());
        assign(groupId, "featured-products", firstProduct.getId());
        assign(groupId, "featured-products", secondProduct.getId());

        mockMvc.perform(put("/api/admin/storefront/navigation-groups/{id}/categories/reorder", groupId)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[%d,%d]}".formatted(secondCategory.id(), firstCategory.id())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].id").value(secondCategory.id()));
        mockMvc.perform(put("/api/admin/storefront/navigation-groups/{id}/featured-products/reorder", groupId)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[%d,%d]}".formatted(secondProduct.getId(), firstProduct.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.featuredProducts[0].id").value(secondProduct.getId()));

        mockMvc.perform(patch("/api/admin/storefront/navigation-groups/{id}/activate", groupId)
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/public/storefront"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.navigationGroups[0].label").value("Furniture"));

        mockMvc.perform(delete("/api/admin/storefront/navigation-groups/{id}/categories/{categoryId}", groupId, firstCategory.id())
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/storefront/navigation-groups/{id}/featured-products/{productId}", groupId, firstProduct.getId())
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/storefront/navigation-groups/{id}", groupId)
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void validationReturnsUsefulClientErrors() throws Exception {
        mockMvc.perform(post("/api/admin/storefront/navigation-groups")
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"\",\"active\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());

        mockMvc.perform(put("/api/admin/storefront/navigation-groups/999999")
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Missing\",\"active\":true}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Navigation group not found."));

        MockMultipartFile text = new MockMultipartFile("image", "hero.txt", MediaType.TEXT_PLAIN_VALUE, "bad".getBytes());
        mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(text).param("altText", "Hero").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    private void assign(long groupId, String resource, long id) throws Exception {
        mockMvc.perform(post("/api/admin/storefront/navigation-groups/{id}/" + resource, groupId)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + id + "}"))
            .andExpect(status().isOk());
    }
}
