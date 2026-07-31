package com.aivle.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.legal-service")
public record LegalServiceProperties(
        String provider,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxInputCharacters,
        int maxResponseBytes
) {
    public LegalServiceProperties {
        if (connectTimeout == null
            || readTimeout == null
            || connectTimeout.isNegative()
            || connectTimeout.isZero()
            || readTimeout.isNegative()
            || readTimeout.isZero()
            || maxInputCharacters <= 0
            || maxResponseBytes <= 0) {
            throw new IllegalArgumentException("Legal service properties are invalid");
        }
    }
}
