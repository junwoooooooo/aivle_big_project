package com.aivle.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String model,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        int maxInputCharacters,
        int maxResponseBytes
) {
    public AiProperties {
        if (connectTimeout == null
            || readTimeout == null
            || connectTimeout.isNegative()
            || connectTimeout.isZero()
            || readTimeout.isNegative()
            || readTimeout.isZero()
            || maxRetries < 0
            || maxInputCharacters <= 0
            || maxResponseBytes <= 0) {
            throw new IllegalArgumentException("AI properties are invalid");
        }
    }
}
