package com.aivle.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private static final String[] ALLOWED_METHODS = {
            "GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"
    };
    private static final String[] PRODUCTION_HEADERS = {
            "Content-Type", "Authorization", "X-Request-Id", "Idempotency-Key",
            "Last-Event-ID"
    };
    private static final String[] DEVELOPMENT_HEADERS = {
            "Content-Type", "Authorization", "X-User-Id", "X-User-Role",
            "X-Request-Id", "Idempotency-Key", "Last-Event-ID"
    };

    private final CorsProperties corsProperties;
    private final Environment environment;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders(allowedHeaders())
                .allowCredentials(corsProperties.allowCredentials())
                .maxAge(3600);
    }

    private String[] allowedHeaders() {
        return environment.matchesProfiles("test", "dev-header-auth")
            ? DEVELOPMENT_HEADERS
            : PRODUCTION_HEADERS;
    }
}
