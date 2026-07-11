package com.builtbygrain.backend.product;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductRequest(
    @NotBlank
    @Size(max = 160)
    String name,

    @NotBlank
    @Size(max = 180)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
    String slug,

    @Size(max = 1000)
    String description,

    @NotNull
    @Positive
    Long priceCents,

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    String currency,

    Boolean inStock,

    @Size(max = 20)
    List<@NotBlank @Size(max = 40) String> sizes,

    Boolean active
) {
    public ProductRequest {
        if (sizes != null) {
            sizes = sizes.stream()
                .map(size -> size == null ? null : size.trim())
                .distinct()
                .toList();
        }
    }
}
