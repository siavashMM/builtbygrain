package com.builtbygrain.backend.product;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.builtbygrain.backend.storage.ObjectStorage;
import com.builtbygrain.backend.storage.ObjectStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectStorage objectStorage;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void publicProductsReturnsOnlyActiveProducts() throws Exception {
        productRepository.save(new Product("Active Bowl", "active-bowl", "Walnut bowl", 3200, "EUR", "/active.jpg"));

        Product inactive = new Product("Inactive Board", "inactive-board", "Archived board", 4100, "EUR", null);
        inactive.deactivate();
        productRepository.save(inactive);

        mockMvc.perform(get("/api/public/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Active Bowl"))
            .andExpect(jsonPath("$[0].slug").value("active-bowl"))
            .andExpect(jsonPath("$[0].fromPriceCents").value(3200))
            .andExpect(jsonPath("$[0].primaryImageUrl").value("/active.jpg"))
            .andExpect(jsonPath("$[0].hoverImageUrl").value("/active.jpg"))
            .andExpect(jsonPath("$[0].colorSwatches").isArray());
    }

    @Test
    void publicProductBySlugReturnsOnlyActiveProduct() throws Exception {
        productRepository.save(new Product("Active Bowl", "active-bowl", "Walnut bowl", 3200, "EUR", null));

        Product inactive = new Product("Inactive Board", "inactive-board", "Archived board", 4100, "EUR", null);
        inactive.deactivate();
        productRepository.save(inactive);

        mockMvc.perform(get("/api/public/products/{slug}", "active-bowl"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Active Bowl"));

        mockMvc.perform(get("/api/public/products/{slug}", "inactive-board"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/public/products/{slug}", "missing-product"))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminCanCreateProduct() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Oak Serving Board",
                      "slug": "oak-serving-board",
                      "description": "Handmade board",
                      "priceCents": 4900,
                      "currency": "EUR",
                      "imageUrl": "/oak.jpg",
                      "inStock": true,
                      "sizes": ["S", "M"],
                      "active": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Oak Serving Board"))
            .andExpect(jsonPath("$.slug").value("oak-serving-board"))
            .andExpect(jsonPath("$.priceCents").value(4900))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.imageUrl").isEmpty())
            .andExpect(jsonPath("$.imageUrls").isArray())
            .andExpect(jsonPath("$.inStock").value(true))
            .andExpect(jsonPath("$.sizes[0]").value("S"))
            .andExpect(jsonPath("$.sizes[1]").value("M"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void nonAdminCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(user("user").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Oak Serving Board", "oak-serving-board", 4900, true)))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllProductsIncludingInactiveProducts() throws Exception {
        productRepository.save(new Product("Active Bowl", "active-bowl", "Walnut bowl", 3200, "EUR", null));

        Product inactive = new Product("Inactive Board", "inactive-board", "Archived board", 4100, "EUR", null);
        inactive.deactivate();
        productRepository.save(inactive);

        mockMvc.perform(get("/api/admin/products").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Active Bowl"))
            .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void nonAdminCannotListAdminProducts() throws Exception {
        mockMvc.perform(get("/api/admin/products").with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Oak Serving Board", "oak-serving-board", 4900, true)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserCannotChangeExistingProductThroughAnyAdminApi() throws Exception {
        Product product = productRepository.save(
            new Product("Protected Board", "protected-board", "Original", 4900, "EUR", "/protected.jpg")
        );
        MockMultipartFile image = new MockMultipartFile(
            "images", "attack.png", MediaType.IMAGE_PNG_VALUE, "attack".getBytes()
        );

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Hacked", "hacked", 1, false)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/products/{id}/deactivate", product.getId()).with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/products/{id}", product.getId()).with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId()).file(image).with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/products/{id}/images/0", product.getId()).with(csrf()))
            .andExpect(status().isUnauthorized());

        Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Protected Board");
        assertThat(unchanged.getPriceCents()).isEqualTo(4900);
        assertThat(unchanged.isActive()).isTrue();
        mockMvc.perform(get("/api/admin/products").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].imageUrls.length()").value(1));
    }

    @Test
    void normalUserCannotChangeExistingProductThroughAnyAdminApi() throws Exception {
        Product product = productRepository.save(
            new Product("Protected Shelf", "protected-shelf", "Original", 6900, "EUR", "/protected.jpg")
        );
        MockMultipartFile image = new MockMultipartFile(
            "images", "attack.png", MediaType.IMAGE_PNG_VALUE, "attack".getBytes()
        );

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                .with(user("user").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Hacked", "hacked", 1, false)))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/products/{id}/activate", product.getId())
                .with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/products/{id}", product.getId())
                .with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId())
                .file(image)
                .with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/products/{id}/images/0", product.getId())
                .with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());

        Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Protected Shelf");
        assertThat(unchanged.getPriceCents()).isEqualTo(6900);
        assertThat(unchanged.isActive()).isTrue();
        mockMvc.perform(get("/api/admin/products").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].imageUrls.length()").value(1));
    }

    @Test
    void nonAdminsCannotCreateProductWithMultipartImageApi() throws Exception {
        MockMultipartFile product = new MockMultipartFile(
            "product", "product.json", MediaType.APPLICATION_JSON_VALUE,
            validProductJson("Unauthorized", "unauthorized", 100, true).getBytes()
        );
        MockMultipartFile image = new MockMultipartFile(
            "images", "attack.png", MediaType.IMAGE_PNG_VALUE, "attack".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/products/with-images").file(product).file(image).with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/products/with-images")
                .file(product)
                .file(image)
                .with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());

        assertThat(productRepository.existsBySlug("unauthorized")).isFalse();
    }

    @Test
    void adminCanUpdateProduct() throws Exception {
        Product product = productRepository.save(new Product("Old Name", "old-name", "Old description", 2500, "EUR", null));

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("New Name", "new-name", 3900, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(product.getId()))
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.slug").value("new-name"))
            .andExpect(jsonPath("$.priceCents").value(3900))
            .andExpect(jsonPath("$.inStock").value(false))
            .andExpect(jsonPath("$.sizes[0]").value("Large"))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminCanDeactivateProduct() throws Exception {
        Product product = productRepository.save(new Product("Oak Board", "oak-board", "Board", 4900, "EUR", null));

        mockMvc.perform(patch("/api/admin/products/{id}/deactivate", product.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/public/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminCanActivateProduct() throws Exception {
        Product product = new Product("Oak Board", "oak-board", "Board", 4900, "EUR", null);
        product.deactivate();
        product = productRepository.save(product);

        mockMvc.perform(patch("/api/admin/products/{id}/activate", product.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCanDeleteProduct() throws Exception {
        Product product = productRepository.save(new Product("Oak Board", "oak-board", "Board", 4900, "EUR", null));

        mockMvc.perform(delete("/api/admin/products/{id}", product.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/products").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingProductCascadesVariantsAndRemovesImagesAfterCommit() throws Exception {
        Product product = productRepository.save(new Product(
            "Cascading Desk", "cascading-desk", "Desk", 4900, "EUR", null));
        String key = "products/00000000-0000-0000-0000-000000000099.png";
        objectStorage.put(key, TestImages.png(), MediaType.IMAGE_PNG_VALUE, "test-sha");
        jdbc.update("""
            INSERT INTO product_images(product_id,image_url,display_order,shared,active,created_at)
            VALUES(?,?,0,TRUE,TRUE,CURRENT_TIMESTAMP)
            """, product.getId(), "/api/public/uploads/" + key);
        jdbc.update("""
            INSERT INTO product_variants(public_id,product_id,combination_key,regular_price_cents,
                stock_quantity,availability_status,active,allow_backorder,created_at,updated_at)
            VALUES('delete-cascade',?,'delete-cascade',4900,1,'IN_STOCK',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, product.getId());

        mockMvc.perform(delete("/api/admin/products/{id}", product.getId())
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_variants WHERE product_id=?", Integer.class, product.getId()
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_images WHERE product_id=?", Integer.class, product.getId()
        )).isZero();
        assertThatThrownBy(() -> objectStorage.head(key))
            .isInstanceOf(ObjectStorageException.class)
            .matches(error -> ((ObjectStorageException) error).isNotFound());
    }

    @Test
    void adminCanUploadProductImage() throws Exception {
        Product product = productRepository.save(new Product("Oak Board", "oak-board", "Board", 4900, "EUR", null));
        MockMultipartFile image = new MockMultipartFile(
            "image",
            "oak.png",
            MediaType.IMAGE_PNG_VALUE,
            TestImages.png()
        );

        mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId())
                .file(new MockMultipartFile("images", image.getOriginalFilename(), image.getContentType(), image.getBytes()))
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("/api/public/uploads/products/")))
            .andExpect(jsonPath("$.imageUrls.length()").value(1));
    }

    @Test
    void adminImageUploadRejectsUnsupportedFileType() throws Exception {
        Product product = productRepository.save(new Product("Oak Board", "oak-board", "Board", 4900, "EUR", null));
        MockMultipartFile image = new MockMultipartFile(
            "image",
            "oak.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId())
                .file(new MockMultipartFile("images", image.getOriginalFilename(), image.getContentType(), image.getBytes()))
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanCreateProductWithMultipleImages() throws Exception {
        MockMultipartFile product = new MockMultipartFile(
            "product",
            "product.json",
            MediaType.APPLICATION_JSON_VALUE,
            validProductJson("Photo Board", "photo-board", 4900, true).getBytes()
        );
        MockMultipartFile first = new MockMultipartFile(
            "images", "front.png", MediaType.IMAGE_PNG_VALUE, TestImages.png()
        );
        MockMultipartFile second = new MockMultipartFile(
            "images", "side.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg()
        );

        mockMvc.perform(multipart("/api/admin/products/with-images")
                .file(product)
                .file(first)
                .file(second)
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrls.length()").value(2))
            .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("/api/public/uploads/products/")));
    }

    @Test
    void invalidProductInputReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "slug": "",
                      "description": "Invalid price",
                      "priceCents": -1,
                      "currency": "EUR",
                      "active": true
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSlugReturnsConflict() throws Exception {
        productRepository.save(new Product("Oak Board", "oak-board", "Board", 4900, "EUR", null));

        mockMvc.perform(post("/api/admin/products")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson("Second Oak Board", "oak-board", 5900, true)))
            .andExpect(status().isConflict());
    }

    private String validProductJson(String name, String slug, long priceCents, boolean active) {
        return """
            {
              "name": "%s",
              "slug": "%s",
              "description": "Handmade product",
              "priceCents": %d,
              "currency": "EUR",
              "imageUrl": "/product.jpg",
              "inStock": false,
              "sizes": ["Large"],
              "active": %s
            }
            """.formatted(name, slug, priceCents, active);
    }
}
