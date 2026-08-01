package com.builtbygrain.backend.storefront;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontAuthorizationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void publicAggregateIsAnonymous() throws Exception {
        mockMvc.perform(get("/api/public/storefront")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/storefront/settings").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    void everyStorefrontAdministrationEndpointRequiresAdmin() throws Exception {
        List<Supplier<MockHttpServletRequestBuilder>> requests = List.of(
            () -> get("/api/admin/storefront/settings"),
            () -> put("/api/admin/storefront/settings").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> get("/api/admin/storefront/navigation-groups"),
            () -> post("/api/admin/storefront/navigation-groups").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> put("/api/admin/storefront/navigation-groups/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> delete("/api/admin/storefront/navigation-groups/1"),
            () -> patch("/api/admin/storefront/navigation-groups/1/activate"),
            () -> patch("/api/admin/storefront/navigation-groups/1/deactivate"),
            () -> put("/api/admin/storefront/navigation-groups/reorder").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> post("/api/admin/storefront/navigation-groups/1/categories").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> delete("/api/admin/storefront/navigation-groups/1/categories/1"),
            () -> put("/api/admin/storefront/navigation-groups/1/categories/reorder").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> post("/api/admin/storefront/navigation-groups/1/featured-products").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> delete("/api/admin/storefront/navigation-groups/1/featured-products/1"),
            () -> put("/api/admin/storefront/navigation-groups/1/featured-products/reorder").contentType(MediaType.APPLICATION_JSON).content("{}")
        );

        for (Supplier<MockHttpServletRequestBuilder> request : requests) {
            mockMvc.perform(request.get().with(csrf())).andExpect(status().isUnauthorized());
            mockMvc.perform(request.get().with(user("user").roles("USER")).with(csrf())).andExpect(status().isForbidden());
        }
        mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image").with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/storefront/settings/hero-image")
                .with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
    }
}
