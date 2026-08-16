package com.builtbygrain.backend.product;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.builtbygrain.backend.performance.PublicCacheNames;
import com.builtbygrain.backend.performance.ReadFromReplica;

@Service
public class ProductCardService {

    private static final String PLACEHOLDER = "/product-placeholder.svg";
    private static final String ACTIVE_PRODUCTS = """
        WITH RECURSIVE category_paths(id, parent_id, slug, path, active_ancestry) AS (
            SELECT id, parent_id, slug, CAST(slug AS VARCHAR(2048)) AS path, active
            FROM categories WHERE parent_id IS NULL
            UNION ALL
            SELECT c.id, c.parent_id, c.slug, CAST(cp.path || '/' || c.slug AS VARCHAR(2048)),
                   cp.active_ancestry AND c.active
            FROM categories c JOIN category_paths cp ON cp.id = c.parent_id
        )
        SELECT p.id, p.name, p.slug, p.currency, p.price_cents,
               primary_image.image_url AS explicit_primary_url,
               hover_image.image_url AS explicit_hover_url,
               c.id category_id, c.name category_name, c.slug category_slug, cp.path category_path,
               MIN(CASE WHEN v.active = TRUE AND v.availability_status <> 'DISCONTINUED'
                   THEN COALESCE(v.sale_price_cents, v.regular_price_cents) END) AS cheapest_variant_price
        FROM products p
        JOIN categories c ON c.id = p.category_id
        JOIN category_paths cp ON cp.id = c.id
        LEFT JOIN product_images primary_image
            ON primary_image.id = p.listing_primary_image_id AND primary_image.active = TRUE
        LEFT JOIN product_images hover_image
            ON hover_image.id = p.listing_hover_image_id AND hover_image.active = TRUE
        LEFT JOIN product_variants v ON v.product_id = p.id
        WHERE p.active = TRUE AND p.status = 'ACTIVE' AND cp.active_ancestry = TRUE
        %s
        GROUP BY p.id, p.name, p.slug, p.currency, p.price_cents,
                 primary_image.image_url, hover_image.image_url,
                 c.id, c.name, c.slug, cp.path
        ORDER BY p.name
        """;

    private final JdbcTemplate jdbc;

    public ProductCardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    @ReadFromReplica
    @Cacheable(cacheNames = PublicCacheNames.PRODUCT_CARDS, key = "'all'", sync = true)
    public List<ProductCardDto> activeCards() {
        return loadCards("", List.of());
    }

    @Transactional(readOnly = true)
    @ReadFromReplica
    public List<ProductCardDto> activeCardsForCategoryIds(Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) return List.of();
        return loadCards("AND p.category_id IN (" + placeholders(categoryIds.size()) + ")", categoryIds);
    }

    @Transactional(readOnly = true)
    @ReadFromReplica
    public List<ProductCardDto> activeCardsForIds(Set<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        return loadCards("AND p.id IN (" + placeholders(productIds.size()) + ")", productIds);
    }

    private List<ProductCardDto> loadCards(String filter, Collection<Long> filterValues) {
        List<ProductRow> products = jdbc.query(
            ACTIVE_PRODUCTS.formatted(filter),
            this::productRow,
            filterValues.toArray()
        );
        if (products.isEmpty()) return List.of();

        List<Long> productIds = products.stream().map(ProductRow::id).toList();
        Map<Long, List<Image>> images = productImages(productIds);
        Map<Long, List<Color>> colors = colors(productIds);
        Map<Long, List<VariantImages>> variants = variants(productIds);

        return products.stream()
            .map(product -> card(
                product,
                variants.getOrDefault(product.id(), List.of()),
                images.getOrDefault(product.id(), List.of()),
                colors.getOrDefault(product.id(), List.of())
            ))
            .toList();
    }

    private ProductCardDto card(
        ProductRow product,
        List<VariantImages> variants,
        List<Image> productImages,
        List<Color> colors
    ) {
        List<ProductCardDto.ColorSwatch> swatches = swatches(colors, variants, productImages);
        String defaultColorPrimary = swatches.isEmpty() ? null : swatches.getFirst().primaryImageUrl();
        VariantImages defaultVariant = variants.stream().findFirst().orElse(null);
        String sharedPrimary = productImages.stream().filter(Image::shared).map(Image::url).findFirst().orElse(null);
        String firstActive = productImages.stream().map(Image::url).findFirst().orElse(null);
        String primary = first(
            product.explicitPrimaryUrl(),
            defaultColorPrimary,
            defaultVariant == null ? null : defaultVariant.first(),
            sharedPrimary,
            firstActive,
            PLACEHOLDER
        );

        String defaultColorHover = swatches.isEmpty() ? null : swatches.getFirst().hoverImageUrl();
        String defaultVariantSecond = defaultVariant == null ? null : defaultVariant.second();
        String secondShared = productImages.stream().filter(Image::shared).skip(1).map(Image::url).findFirst().orElse(null);
        String hover = first(product.explicitHoverUrl(), defaultColorHover, defaultVariantSecond, secondShared, primary);

        long price = product.cheapestVariantPrice() == null
            ? product.fallbackPrice()
            : product.cheapestVariantPrice();
        return new ProductCardDto(
            product.id(), product.name(), product.slug(), product.currency(), price,
            primary, hover, product.categoryId(), product.categoryName(), product.categorySlug(),
            product.categoryPath(), swatches
        );
    }

    private List<ProductCardDto.ColorSwatch> swatches(
        List<Color> colors,
        List<VariantImages> variants,
        List<Image> productImages
    ) {
        String sharedPrimary = productImages.stream().filter(Image::shared).map(Image::url).findFirst().orElse(null);
        String sharedSecond = productImages.stream().filter(Image::shared).skip(1).map(Image::url).findFirst().orElse(null);
        String firstImage = productImages.stream().map(Image::url).findFirst().orElse(null);
        List<ProductCardDto.ColorSwatch> result = new ArrayList<>();
        for (Color color : colors) {
            VariantImages match = variants.stream()
                .filter(variant -> variant.optionValueIds().contains(color.id()))
                .findFirst()
                .orElse(null);
            String primary = first(match == null ? null : match.first(), sharedPrimary, firstImage, PLACEHOLDER);
            String hover = first(match == null ? null : match.second(), sharedSecond, primary);
            result.add(new ProductCardDto.ColorSwatch(
                color.id(), color.label(), color.swatchHex(), color.swatchImageUrl(), primary, hover
            ));
        }
        return result;
    }

    private Map<Long, List<Image>> productImages(List<Long> productIds) {
        Map<Long, List<Image>> result = new LinkedHashMap<>();
        jdbc.query("""
            SELECT product_id, image_url, shared
            FROM product_images
            WHERE active = TRUE AND product_id IN (%s)
            ORDER BY product_id, display_order, id
            """.formatted(placeholders(productIds.size())), (RowCallbackHandler) rs -> result
                .computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                .add(new Image(normalize(rs.getString("image_url")), rs.getBoolean("shared"))),
            productIds.toArray());
        return immutableLists(result);
    }

    private Map<Long, List<Color>> colors(List<Long> productIds) {
        Map<Long, List<Color>> result = new LinkedHashMap<>();
        jdbc.query("""
            SELECT o.product_id, v.id, v.label, v.swatch_hex, v.swatch_image_url
            FROM product_option_values v
            JOIN product_options o ON o.id = v.option_id
            WHERE o.display_type = 'COLOR_SWATCH' AND v.active = TRUE
              AND o.product_id IN (%s)
            ORDER BY o.product_id, o.sort_order, v.sort_order, v.id
            """.formatted(placeholders(productIds.size())), (RowCallbackHandler) rs -> result
                .computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                .add(new Color(
                    rs.getLong("id"), rs.getString("label"), rs.getString("swatch_hex"),
                    normalize(rs.getString("swatch_image_url"))
                )), productIds.toArray());
        return immutableLists(result);
    }

    private Map<Long, List<VariantImages>> variants(List<Long> productIds) {
        Map<Long, LinkedHashMap<Long, VariantImagesBuilder>> rows = new LinkedHashMap<>();
        jdbc.query("""
            SELECT v.product_id, v.id, vv.option_value_id, i.image_url
            FROM product_variants v
            LEFT JOIN product_variant_option_values vv ON vv.variant_id = v.id
            LEFT JOIN product_variant_images vi ON vi.variant_id = v.id
            LEFT JOIN product_images i ON i.id = vi.image_id AND i.active = TRUE
            WHERE v.active = TRUE AND v.availability_status <> 'DISCONTINUED'
              AND v.product_id IN (%s)
            ORDER BY v.product_id, v.legacy_default DESC, v.id,
                     vi.is_primary DESC, vi.sort_order, i.id
            """.formatted(placeholders(productIds.size())), rs -> {
                long productId = rs.getLong("product_id");
                VariantImagesBuilder row = rows
                    .computeIfAbsent(productId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(rs.getLong("id"), ignored -> new VariantImagesBuilder());
                Long optionValueId = (Long) rs.getObject("option_value_id");
                if (optionValueId != null && !row.optionValueIds.contains(optionValueId)) {
                    row.optionValueIds.add(optionValueId);
                }
                String url = normalize(rs.getString("image_url"));
                if (url != null && !row.imageUrls.contains(url)) row.imageUrls.add(url);
            }, productIds.toArray());

        Map<Long, List<VariantImages>> result = new LinkedHashMap<>();
        rows.forEach((productId, variants) -> result.put(
            productId,
            variants.values().stream().map(VariantImagesBuilder::build).toList()
        ));
        return Map.copyOf(result);
    }

    private ProductRow productRow(ResultSet rs, int row) throws SQLException {
        return new ProductRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("currency"),
            rs.getLong("price_cents"),
            normalize(rs.getString("explicit_primary_url")),
            normalize(rs.getString("explicit_hover_url")),
            rs.getLong("category_id"),
            rs.getString("category_name"),
            rs.getString("category_slug"),
            rs.getString("category_path"),
            (Long) rs.getObject("cheapest_variant_price")
        );
    }

    private static <T> Map<Long, List<T>> immutableLists(Map<Long, List<T>> values) {
        return values.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> List.copyOf(entry.getValue())
        ));
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static String normalize(String url) {
        return url != null && url.startsWith("/uploads/") ? "/api/public" + url : url;
    }

    private record ProductRow(
        long id,
        String name,
        String slug,
        String currency,
        long fallbackPrice,
        String explicitPrimaryUrl,
        String explicitHoverUrl,
        long categoryId,
        String categoryName,
        String categorySlug,
        String categoryPath,
        Long cheapestVariantPrice
    ) { }
    private record Image(String url, boolean shared) { }
    private record Color(long id, String label, String swatchHex, String swatchImageUrl) { }
    private record VariantImages(List<Long> optionValueIds, List<String> imageUrls) {
        String first() { return imageUrls.isEmpty() ? null : imageUrls.getFirst(); }
        String second() { return imageUrls.size() < 2 ? null : imageUrls.get(1); }
    }
    private static final class VariantImagesBuilder {
        private final List<Long> optionValueIds = new ArrayList<>();
        private final List<String> imageUrls = new ArrayList<>();

        private VariantImages build() {
            return new VariantImages(List.copyOf(optionValueIds), List.copyOf(imageUrls));
        }
    }
}
