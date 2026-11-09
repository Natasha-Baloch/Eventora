package com.eventora.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${eventora.upload-dir}") private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String loc = Path.of(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(loc);
    }
}
