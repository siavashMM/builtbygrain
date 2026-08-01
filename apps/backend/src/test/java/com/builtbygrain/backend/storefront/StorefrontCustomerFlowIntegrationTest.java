package com.builtbygrain.backend.storefront;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.builtbygrain.backend.product.TestImages;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontCustomerFlowIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired StorefrontImageStorageService images;
    private String uploadedHero;

    @AfterEach
    void removeUploadedHero() {
        if (uploadedHero != null) images.delete(uploadedHero);
    }

    @Test
    void administratorConfigurationDrivesPublicNavigationAndDescendantCategoryPages() throws Exception {
        long office = createCategory("Flow Office", null);
        long desks = createCategory("Flow Desks", office);
        long standing = createCategory("Flow Standing Desks", desks);
        long living = createCategory("Flow Living", null);

        long officeProduct = createProduct(office, "Flow Office Organizer", "flow-office-organizer", 3900);
        long desksProduct = createProduct(desks, "Flow Writing Desk", "flow-writing-desk", 12900);
        long standingProduct = createProduct(standing, "Flow Standing Desk", "flow-standing-desk", 19900);
        long livingProduct = createProduct(living, "Flow Living Shelf", "flow-living-shelf", 7900);

        JsonNode hero = json(mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .file(new MockMultipartFile("image", "flow-hero.png", MediaType.IMAGE_PNG_VALUE, TestImages.png()))
                .param("altText", "Oak desk in a bright workspace").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        uploadedHero = hero.get("heroImageUrl").asText();

        long officeGroup = createGroup("Flow Office", true);
        long livingGroup = createGroup("Flow Living", true);
        assign(officeGroup, "categories", standing);
        assign(officeGroup, "categories", office);
        reorder(officeGroup, "categories", office, standing);
        assign(livingGroup, "categories", living);
        assign(officeGroup, "featured-products", officeProduct);
        assign(officeGroup, "featured-products", desksProduct);
        assign(officeGroup, "featured-products", standingProduct);
        reorder(officeGroup, "featured-products", standingProduct, officeProduct, desksProduct);
        reorderGroups(livingGroup, officeGroup);

        mockMvc.perform(get("/api/public/storefront"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settings.heroImageUrl").value(uploadedHero))
            .andExpect(jsonPath("$.settings.heroImageAltText").value("Oak desk in a bright workspace"))
            .andExpect(jsonPath("$.navigationGroups[*].label", contains("Flow Living", "Flow Office")))
            .andExpect(jsonPath("$.navigationGroups[1].categories[*].id", contains((int) office, (int) standing)))
            .andExpect(jsonPath("$.navigationGroups[1].categories[0].active").doesNotExist())
            .andExpect(jsonPath("$.navigationGroups[1].featuredProducts[*].slug",
                contains("flow-standing-desk", "flow-office-organizer", "flow-writing-desk")))
            .andExpect(jsonPath("$.navigationGroups[1].featuredProducts[0].categoryPath")
                .value("flow-office/flow-desks/flow-standing-desks"));

        assertCategoryProducts("flow-office", "flow-office-organizer", "flow-standing-desk", "flow-writing-desk");
        assertCategoryProducts("flow-office/flow-desks", "flow-standing-desk", "flow-writing-desk");
        // A second direct request exercises the same path used by a hard refresh.
        assertCategoryProducts("flow-office/flow-desks", "flow-standing-desk", "flow-writing-desk");

        mockMvc.perform(patch("/api/admin/categories/{id}/status", desks).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/products/{id}/deactivate", standingProduct).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/storefront/navigation-groups/{id}/deactivate", livingGroup)
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/storefront"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.navigationGroups[*].label", contains("Flow Office")))
            .andExpect(jsonPath("$.navigationGroups[0].categories[*].id", not(hasItem((int) standing))))
            .andExpect(jsonPath("$.navigationGroups[0].featuredProducts[*].slug", not(hasItem("flow-standing-desk"))))
            .andExpect(jsonPath("$.navigationGroups[0].featuredProducts[*].slug", not(hasItem("flow-writing-desk"))));
        mockMvc.perform(get("/api/public/category").param("path", "flow-office/flow-desks"))
            .andExpect(status().isNotFound());
        assertCategoryProducts("flow-office", "flow-office-organizer");

        // Keep the independently configured product referenced so the flow covers products at another root level.
        mockMvc.perform(get("/api/public/category").param("path", "flow-living"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[*].id", hasItem((int) livingProduct)));
    }

    private long createCategory(String name, Long parentId) throws Exception {
        String body = parentId == null ? "{\"name\":\"%s\",\"parentId\":null}".formatted(name)
            : "{\"name\":\"%s\",\"parentId\":%d}".formatted(name, parentId);
        return json(mockMvc.perform(post("/api/admin/categories").queryParam("simple", "true")
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long createProduct(long categoryId, String name, String slug, long price) throws Exception {
        String body = """
            {"name":"%s","slug":"%s","description":null,"priceCents":%d,"currency":"EUR",
             "inStock":true,"sizes":[],"active":true}
            """.formatted(name, slug, price);
        return json(mockMvc.perform(post("/api/admin/categories/{id}/products", categoryId)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long createGroup(String label, boolean active) throws Exception {
        return json(mockMvc.perform(post("/api/admin/storefront/navigation-groups").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"%s\",\"active\":%s}".formatted(label, active)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void assign(long groupId, String resource, long id) throws Exception {
        mockMvc.perform(post("/api/admin/storefront/navigation-groups/{groupId}/" + resource, groupId)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + id + "}"))
            .andExpect(status().isOk());
    }

    private void reorder(long groupId, String resource, long... ids) throws Exception {
        mockMvc.perform(put("/api/admin/storefront/navigation-groups/{groupId}/" + resource + "/reorder", groupId)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":" + mapper.writeValueAsString(ids) + "}"))
            .andExpect(status().isOk());
    }

    private void reorderGroups(long... ids) throws Exception {
        mockMvc.perform(put("/api/admin/storefront/navigation-groups/reorder").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":" + mapper.writeValueAsString(ids) + "}"))
            .andExpect(status().isOk());
    }

    private void assertCategoryProducts(String path, String... slugs) throws Exception {
        mockMvc.perform(get("/api/public/category").param("path", path)).andExpect(status().isOk())
            .andExpect(jsonPath("$.products[*].slug", contains(slugs)));
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }
}
