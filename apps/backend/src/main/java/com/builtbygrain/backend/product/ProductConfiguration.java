package com.builtbygrain.backend.product;

import java.util.List;
import java.util.Map;

public record ProductConfiguration(
    String subtitle,
    String badge,
    Long salePriceCents,
    String unitPriceLabel,
    String deliveryEstimate,
    List<String> benefits,
    Map<String, String> specifications,
    List<ContentSection> sections,
    List<Faq> faqs,
    List<Option> options,
    List<Variant> variants,
    RatingSummary rating,
    Boolean sizeAffectsImages
) {
    public ProductConfiguration {
        benefits = benefits == null ? List.of() : List.copyOf(benefits);
        specifications = specifications == null ? Map.of() : Map.copyOf(specifications);
        sections = sections == null ? List.of() : List.copyOf(sections);
        faqs = faqs == null ? List.of() : List.copyOf(faqs);
        options = options == null ? List.of() : List.copyOf(options);
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public static ProductConfiguration empty() {
        return new ProductConfiguration(null, null, null, null, null, List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), null, true);
    }

    public record Option(String id, String name, String displayType, List<OptionValue> values) {}
    public record OptionValue(String id, String label, String swatchColor, String imageUrl, String additionalInformation) {}
    public record Variant(String id, String sku, List<String> optionValueIds, Long priceCents, Long salePriceCents,
                          Integer stockQuantity, String stockStatus, boolean available, boolean backorderAllowed,
                          boolean preorderAllowed, List<String> imageUrls, String deliveryEstimate) {}
    public record ContentSection(String id, String heading, String content) {}
    public record Faq(String question, String answer) {}
    public record RatingSummary(Double average, Integer count) {}
}
