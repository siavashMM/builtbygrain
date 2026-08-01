package com.builtbygrain.backend.product;

import static com.builtbygrain.backend.catalog.CatalogAdminDtos.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.catalog.CatalogManagementService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductCardServiceTest {
    @Autowired ProductRepository products;
    @Autowired ProductCardService cards;
    @Autowired CatalogManagementService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void resolvesExplicitCardImagesSwatchesAndCheapestActiveVariant() {
        Product product = products.save(new Product("Card Test", "card-test", "Test", 9900, "EUR", null));
        OptionDto color = catalog.createOption(product.getId(), new OptionRequest("Color", "color", "COLOR_SWATCH", 0, true));
        OptionValueDto oak = catalog.createValue(product.getId(), color.id(), new OptionValueRequest("Oak", "oak", "#B78B5E", null, null, 0, true));
        catalog.createValue(product.getId(), color.id(), new OptionValueRequest("Walnut", "walnut", "#5B3927", null, null, 1, true));
        List<VariantDto> variants = catalog.generate(product.getId(), new GenerateRequest(9900L, true));
        VariantDto oakVariant = variants.stream().filter(v -> v.optionValues().stream().anyMatch(value -> value.valueId().equals(oak.id()))).findFirst().orElseThrow();
        VariantDto walnutVariant = variants.stream().filter(v -> !v.id().equals(oakVariant.id())).findFirst().orElseThrow();
        catalog.updateVariant(product.getId(), oakVariant.id(), new VariantUpdateRequest(null, 7600, 7200L, 5, "IN_STOCK", true, false, null));
        catalog.updateVariant(product.getId(), walnutVariant.id(), new VariantUpdateRequest(null, 6900, null, 4, "IN_STOCK", true, false, null));

        long oakPrimary = image(product.getId(), "/oak-primary.jpg", 0, true);
        long oakHover = image(product.getId(), "/oak-hover.jpg", 1, false);
        long explicitPrimary = image(product.getId(), "/listing-primary.jpg", 2, true);
        long explicitHover = image(product.getId(), "/listing-hover.jpg", 3, true);
        catalog.assignImage(product.getId(), oakVariant.id(), new AssignVariantImageRequest(oakPrimary, 0, true));
        catalog.assignImage(product.getId(), oakVariant.id(), new AssignVariantImageRequest(oakHover, 1, false));
        catalog.assignListingImage(product.getId(), "primary", new ListingImageRequest(explicitPrimary));
        catalog.assignListingImage(product.getId(), "hover", new ListingImageRequest(explicitHover));

        ProductCardDto card = cards.activeCards().stream().filter(item -> item.id().equals(product.getId())).findFirst().orElseThrow();
        assertThat(card.fromPriceCents()).isEqualTo(6900);
        assertThat(card.primaryImageUrl()).isEqualTo("/listing-primary.jpg");
        assertThat(card.hoverImageUrl()).isEqualTo("/listing-hover.jpg");
        assertThat(card.colorSwatches()).extracting(ProductCardDto.ColorSwatch::label).containsExactly("Oak", "Walnut");
        assertThat(card.colorSwatches().getFirst().primaryImageUrl()).isEqualTo("/oak-primary.jpg");
        assertThat(card.colorSwatches().getFirst().hoverImageUrl()).isEqualTo("/oak-hover.jpg");

        long replacement = image(product.getId(), "/oak-replacement.jpg", 4, true);
        VariantDto updated = catalog.assignImage(product.getId(), oakVariant.id(), new AssignVariantImageRequest(replacement, 0, true));
        assertThat(updated.primaryImageUrl()).isEqualTo("/oak-replacement.jpg");
        assertThat(updated.imageUrls()).containsExactly("/oak-replacement.jpg", "/oak-hover.jpg");
    }

    @Test
    void deletingCatalogImageClearsEveryAssignmentAndCompactsGalleryOrder() {
        Product product = products.save(new Product("Delete Image Test", "delete-image-test", "Test", 9900, "EUR", null));
        OptionDto finish = catalog.createOption(product.getId(), new OptionRequest("Finish", "finish", "BUTTON", 0, true));
        catalog.createValue(product.getId(), finish.id(), new OptionValueRequest("Oak", "oak", null, null, null, 0, true));
        VariantDto variant = catalog.generate(product.getId(), new GenerateRequest(9900L, true)).getFirst();
        long deletedImage = image(product.getId(), "/delete-everywhere.jpg", 0, true);
        long remainingImage = image(product.getId(), "/keep.jpg", 1, true);
        catalog.assignListingImage(product.getId(), "primary", new ListingImageRequest(deletedImage));
        catalog.assignListingImage(product.getId(), "hover", new ListingImageRequest(deletedImage));
        catalog.assignImage(product.getId(), variant.id(), new AssignVariantImageRequest(deletedImage, 0, true));

        catalog.deleteImage(product.getId(), deletedImage);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE id=?", Integer.class, deletedImage)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant_images WHERE image_id=?", Integer.class, deletedImage)).isZero();
        assertThat(jdbc.queryForObject("SELECT listing_primary_image_id FROM products WHERE id=?", Long.class, product.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT listing_hover_image_id FROM products WHERE id=?", Long.class, product.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT display_order FROM product_images WHERE id=?", Integer.class, remainingImage)).isZero();
        assertThat(catalog.variants(product.getId()).getFirst().primaryImageUrl()).isNull();
    }

    @Test
    void productCardQueriesStayFixedAtFourForOneTenAndOneHundredProducts() {
        Set<Long> ids = new LinkedHashSet<>();
        for (int index = 0; index < 100; index++) {
            Product product = products.save(new Product(
                "Query Count " + index, "query-count-" + index, "Test", 1000 + index, "EUR", null));
            ids.add(product.getId());
        }
        products.flush();

        CountingJdbcTemplate counting = new CountingJdbcTemplate(dataSource);
        ProductCardService bounded = new ProductCardService(counting);
        for (int size : List.of(1, 10, 100)) {
            Set<Long> selected = ids.stream().limit(size).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            counting.reset();
            assertThat(bounded.activeCardsForIds(selected)).hasSize(size);
            assertThat(counting.queryCount()).as("queries for %s products", size).isEqualTo(4);
        }
    }

    private long image(long productId, String url, int order, boolean shared) {
        jdbc.update("INSERT INTO product_images(product_id,image_url,display_order,shared,active,created_at) VALUES(?,?,?,?,TRUE,CURRENT_TIMESTAMP)",
            productId, url, order, shared);
        return jdbc.queryForObject("SELECT id FROM product_images WHERE product_id=? AND display_order=?", Long.class, productId, order);
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queryCount;

        private CountingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount++;
            return super.query(sql, rowMapper, args);
        }

        @Override
        public void query(String sql, RowCallbackHandler rowCallbackHandler, Object... args) {
            queryCount++;
            super.query(sql, rowCallbackHandler, args);
        }

        private int queryCount() { return queryCount; }
        private void reset() { queryCount = 0; }
    }
}
