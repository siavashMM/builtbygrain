package com.builtbygrain.backend.product;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProductUploadWebConfig implements WebMvcConfigurer {

    private final Path uploadRoot;

    public ProductUploadWebConfig(@Value("${app.uploads.root:uploads}") String uploadRoot) {
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = uploadRoot.toUri().toString();
        if (!uploadLocation.endsWith("/")) {
            uploadLocation = uploadLocation + "/";
        }

        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(uploadLocation);
        registry.addResourceHandler("/api/public/uploads/**")
            .addResourceLocations(uploadLocation);
    }
}
