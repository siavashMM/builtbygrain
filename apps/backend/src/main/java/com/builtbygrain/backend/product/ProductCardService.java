package com.builtbygrain.backend.product;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCardService {
    private static final String PLACEHOLDER = "/product-placeholder.svg";

    private final JdbcTemplate jdbc;

    public ProductCardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ProductCardDto> activeCards() {
        return jdbc.query("""
            WITH RECURSIVE category_paths(id, parent_id, slug, path, active_ancestry) AS (
                SELECT id, parent_id, slug, CAST(slug AS VARCHAR(2048)) AS path, active
                FROM categories WHERE parent_id IS NULL
                UNION ALL
                SELECT c.id, c.parent_id, c.slug, CAST(cp.path || '/' || c.slug AS VARCHAR(2048)),
                       cp.active_ancestry AND c.active
                FROM categories c JOIN category_paths cp ON cp.id = c.parent_id
            )
            SELECT p.id, p.name, p.slug, p.currency, p.price_cents,
                   p.listing_primary_image_id, p.listing_hover_image_id,
                   c.id category_id, c.name category_name, c.slug category_slug, cp.path category_path
            FROM products p
            JOIN categories c ON c.id = p.category_id
            JOIN category_paths cp ON cp.id = c.id
            WHERE p.active = TRUE AND p.status = 'ACTIVE' AND cp.active_ancestry = TRUE
            ORDER BY p.name
            """, (rs, row) -> card(rs));
    }

    private ProductCardDto card(ResultSet rs) throws SQLException {
        long productId = rs.getLong("id");
        List<VariantImages> variants = variants(productId);
        List<Image> productImages = productImages(productId);
        List<ProductCardDto.ColorSwatch> swatches = swatches(productId, variants, productImages);

        String explicitPrimary = imageUrl((Long) rs.getObject("listing_primary_image_id"));
        String defaultColorPrimary = swatches.isEmpty() ? null : swatches.getFirst().primaryImageUrl();
        VariantImages defaultVariant = variants.stream().findFirst().orElse(null);
        String sharedPrimary = productImages.stream().filter(Image::shared).map(Image::url).findFirst().orElse(null);
        String firstActive = productImages.stream().map(Image::url).findFirst().orElse(null);
        String primary = first(explicitPrimary, defaultColorPrimary,
            defaultVariant == null ? null : defaultVariant.first(), sharedPrimary, firstActive, PLACEHOLDER);

        String explicitHover = imageUrl((Long) rs.getObject("listing_hover_image_id"));
        String defaultColorHover = swatches.isEmpty() ? null : swatches.getFirst().hoverImageUrl();
        String defaultVariantSecond = defaultVariant == null ? null : defaultVariant.second();
        String secondShared = productImages.stream().filter(Image::shared).skip(1).map(Image::url).findFirst().orElse(null);
        String hover = first(explicitHover, defaultColorHover, defaultVariantSecond, secondShared, primary);

        Long cheapest = jdbc.query("""
            SELECT MIN(COALESCE(v.sale_price_cents, v.regular_price_cents))
            FROM product_variants v
            WHERE v.product_id = ? AND v.active = TRUE AND v.availability_status <> 'DISCONTINUED'
            """, r -> r.next() ? (Long) r.getObject(1) : null, productId);

        return new ProductCardDto(productId, rs.getString("name"), rs.getString("slug"), rs.getString("currency"),
            cheapest == null ? rs.getLong("price_cents") : cheapest, primary, hover,
            rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_slug"),
            rs.getString("category_path"), swatches);
    }

    private List<ProductCardDto.ColorSwatch> swatches(long productId, List<VariantImages> variants, List<Image> productImages) {
        List<Color> colors = jdbc.query("""
            SELECT v.id, v.label, v.swatch_hex, v.swatch_image_url
            FROM product_option_values v
            JOIN product_options o ON o.id = v.option_id
            WHERE o.product_id = ? AND o.display_type = 'COLOR_SWATCH' AND v.active = TRUE
            ORDER BY o.sort_order, v.sort_order, v.id
            """, (rs, row) -> new Color(rs.getLong(1), rs.getString(2), rs.getString(3), normalize(rs.getString(4))), productId);

        String sharedPrimary = productImages.stream().filter(Image::shared).map(Image::url).findFirst().orElse(null);
        String sharedSecond = productImages.stream().filter(Image::shared).skip(1).map(Image::url).findFirst().orElse(null);
        List<ProductCardDto.ColorSwatch> result = new ArrayList<>();
        for (Color color : colors) {
            VariantImages match = variants.stream().filter(v -> v.optionValueIds().contains(color.id())).findFirst().orElse(null);
            String primary = first(match == null ? null : match.first(), sharedPrimary, productImages.stream().map(Image::url).findFirst().orElse(null), PLACEHOLDER);
            String hover = first(match == null ? null : match.second(), sharedSecond, primary);
            result.add(new ProductCardDto.ColorSwatch(color.id(), color.label(), color.swatchHex(), color.swatchImageUrl(), primary, hover));
        }
        return result;
    }

    private List<VariantImages> variants(long productId) {
        Map<Long, VariantImagesBuilder> rows = new LinkedHashMap<>();
        jdbc.query("""
            SELECT v.id, vv.option_value_id, i.image_url
            FROM product_variants v
            LEFT JOIN product_variant_option_values vv ON vv.variant_id = v.id
            LEFT JOIN product_variant_images vi ON vi.variant_id = v.id
            LEFT JOIN product_images i ON i.id = vi.image_id AND i.active = TRUE
            WHERE v.product_id = ? AND v.active = TRUE AND v.availability_status <> 'DISCONTINUED'
            ORDER BY v.legacy_default DESC, v.id, vi.is_primary DESC, vi.sort_order, i.id
            """, rs -> {
                VariantImagesBuilder row = rows.computeIfAbsent(rs.getLong("id"), ignored -> new VariantImagesBuilder());
                Long optionValueId = (Long) rs.getObject("option_value_id");
                if (optionValueId != null && !row.optionValueIds.contains(optionValueId)) row.optionValueIds.add(optionValueId);
                String url = normalize(rs.getString("image_url"));
                if (url != null && !row.imageUrls.contains(url)) row.imageUrls.add(url);
            }, productId);
        return rows.values().stream().map(row -> new VariantImages(List.copyOf(row.optionValueIds), List.copyOf(row.imageUrls))).toList();
    }

    private List<Image> productImages(long productId) {
        return jdbc.query("""
            SELECT image_url, shared FROM product_images
            WHERE product_id = ? AND active = TRUE
            ORDER BY display_order, id
            """, (rs, row) -> new Image(normalize(rs.getString(1)), rs.getBoolean(2)), productId);
    }

    private String imageUrl(Long imageId) {
        if (imageId == null) return null;
        return jdbc.query("SELECT image_url FROM product_images WHERE id = ? AND active = TRUE",
            (rs, row) -> normalize(rs.getString(1)), imageId).stream().findFirst().orElse(null);
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static String normalize(String url) {
        return url != null && url.startsWith("/uploads/") ? "/api/public" + url : url;
    }

    private record Image(String url, boolean shared) {}
    private record Color(long id, String label, String swatchHex, String swatchImageUrl) {}
    private record VariantImages(List<Long> optionValueIds, List<String> imageUrls) {
        String first() { return imageUrls.isEmpty() ? null : imageUrls.getFirst(); }
        String second() { return imageUrls.size() < 2 ? null : imageUrls.get(1); }
    }
    private static final class VariantImagesBuilder {
        private final List<Long> optionValueIds = new ArrayList<>();
        private final List<String> imageUrls = new ArrayList<>();
    }
}
