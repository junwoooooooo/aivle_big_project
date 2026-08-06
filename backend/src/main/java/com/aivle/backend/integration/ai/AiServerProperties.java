package com.aivle.backend.integration.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-server")
public record AiServerProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    String internalApiKey
) {
    public AiServerProperties {
        baseUrl = blank(baseUrl)
            ? "http://127.0.0.1:8000"
            : stripTrailingSlash(baseUrl.trim());
        connectTimeout = connectTimeout == null
            ? Duration.ofSeconds(3)
            : connectTimeout;
        readTimeout = readTimeout == null
            ? Duration.ofSeconds(30)
            : readTimeout;
        internalApiKey = internalApiKey == null
            ? ""
            : internalApiKey.trim();

        if (
            connectTimeout.isZero()
            || connectTimeout.isNegative()
            || readTimeout.isZero()
            || readTimeout.isNegative()
        ) {
            throw new IllegalArgumentException(
                "AI server timeouts must be positive"
            );
        }
    }

    public boolean hasInternalApiKey() {
        return !internalApiKey.isBlank();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
