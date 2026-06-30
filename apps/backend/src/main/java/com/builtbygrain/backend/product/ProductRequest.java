package com.builtbygrain.backend.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductRequest(
    @NotBlank
    @Size(max = 160)
    String name,

    @Size(max = 1000)
    String description,

    @NotNull
    @Positive
    Long priceCents,

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    String currency
) {
}
