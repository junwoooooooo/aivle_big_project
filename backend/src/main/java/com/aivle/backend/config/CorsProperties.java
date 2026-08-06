package com.aivle.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        boolean allowCredentials
) {
    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
