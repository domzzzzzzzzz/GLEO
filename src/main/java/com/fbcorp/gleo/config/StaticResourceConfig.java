package com.fbcorp.gleo.config;

import com.fbcorp.gleo.service.AssetStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final AssetStorageService assetStorageService;

    public StaticResourceConfig(AssetStorageService assetStorageService) {
        this.assetStorageService = assetStorageService;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String rootLocation = assetStorageService.getRootDir().toUri().toString();
        if (!rootLocation.endsWith("/")) {
            rootLocation += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(rootLocation);
    }
}

