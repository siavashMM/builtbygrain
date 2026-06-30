package com.builtbygrain.backend.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void publicProductsReturnsOnlyActiveProducts() throws Exception {
        productRepository.save(new Product("Active Bowl", "Walnut bowl", 3200, "EUR"));

        Product inactive = new Product("Inactive Board", "Archived board", 4100, "EUR");
        inactive.deactivate();
        productRepository.save(inactive);

        mockMvc.perform(get("/api/public/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Active Bowl"))
            .andExpect(jsonPath("$[0].priceCents").value(3200))
            .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void adminCanCreateProduct() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Oak Serving Board",
                      "description": "Handmade board",
                      "priceCents": 4900,
                      "currency": "EUR"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Oak Serving Board"))
            .andExpect(jsonPath("$.priceCents").value(4900))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void nonAdminCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("user", "password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Oak Serving Board", 4900)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Oak Serving Board", 4900)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanUpdateProduct() throws Exception {
        Product product = productRepository.save(new Product("Old Name", "Old description", 2500, "EUR"));

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("New Name", 3900)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(product.getId()))
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.priceCents").value(3900));
    }

    @Test
    void adminCanDeactivateProduct() throws Exception {
        Product product = productRepository.save(new Product("Oak Board", "Board", 4900, "EUR"));

        mockMvc.perform(patch("/api/admin/products/{id}/deactivate", product.getId())
                .with(httpBasic("admin", "admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/public/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidProductInputReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "description": "Invalid price",
                      "priceCents": -1,
                      "currency": "EUR"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    private String validProductJson(String name, long priceCents) {
        return """
            {
              "name": "%s",
              "description": "Handmade product",
              "priceCents": %d,
              "currency": "EUR"
            }
            """.formatted(name, priceCents);
    }
}
