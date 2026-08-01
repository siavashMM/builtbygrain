package com.builtbygrain.backend.product;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ProductConfigurationConverter implements AttributeConverter<ProductConfiguration, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ProductConfiguration value) {
        try {
            return MAPPER.writeValueAsString(value == null ? ProductConfiguration.empty() : value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid product configuration", exception);
        }
    }

    @Override
    public ProductConfiguration convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return ProductConfiguration.empty();
        try {
            return MAPPER.readValue(value, ProductConfiguration.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid stored product configuration", exception);
        }
    }
}
