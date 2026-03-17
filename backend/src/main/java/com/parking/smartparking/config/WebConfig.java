package com.parking.smartparking.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Comma-separated list. Can be overridden via env var: APP_CORS_ALLOWED_ORIGINS
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5500,http://127.0.0.1:5500}")
    private String allowedOrigins;

    @SuppressWarnings("null")
    private String[] parseAllowedOrigins(String csv) {
        final String safeCsv = (csv == null) ? "" : csv;
        return Arrays.stream(safeCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    @SuppressWarnings("null")
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        final String[] origins = parseAllowedOrigins(allowedOrigins);

        registry.addMapping("/**")
            // Old (kept): hardcoded dev origins
            // .allowedOrigins(
            //         "http://localhost:3000",
            //         "http://127.0.0.1:3000",
            //         "http://localhost:5500", // Live Server port
            //         "http://127.0.0.1:5500"
            // )
            .allowedOrigins(origins)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
