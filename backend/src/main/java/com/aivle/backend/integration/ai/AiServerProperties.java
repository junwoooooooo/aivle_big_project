package com.aivle.backend.integration.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.ai-server")
public record AiServerProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration longReadTimeout,
    Duration marketResearchReadTimeout,
    Duration conceptPortfolioReadTimeout,
    Duration twinSurveyReadTimeout,
    String internalApiKey
) {
    @ConstructorBinding
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
        longReadTimeout = longReadTimeout == null
            ? Duration.ofMinutes(7)
            : longReadTimeout;
        marketResearchReadTimeout = marketResearchReadTimeout == null
            ? Duration.ofMinutes(22)
            : marketResearchReadTimeout;
        conceptPortfolioReadTimeout = conceptPortfolioReadTimeout == null
            ? Duration.ofMinutes(15)
            : conceptPortfolioReadTimeout;
        twinSurveyReadTimeout = twinSurveyReadTimeout == null
            ? Duration.ofMinutes(14)
            : twinSurveyReadTimeout;
        internalApiKey = internalApiKey == null
            ? ""
            : internalApiKey.trim();

        if (
            connectTimeout.isZero()
            || connectTimeout.isNegative()
            || readTimeout.isZero()
            || readTimeout.isNegative()
            || longReadTimeout.isZero()
            || longReadTimeout.isNegative()
            || marketResearchReadTimeout.isZero()
            || marketResearchReadTimeout.isNegative()
            || conceptPortfolioReadTimeout.isZero()
            || conceptPortfolioReadTimeout.isNegative()
            || twinSurveyReadTimeout.isZero()
            || twinSurveyReadTimeout.isNegative()
        ) {
            throw new IllegalArgumentException(
                "AI server timeouts must be positive"
            );
        }
    }

    /**
     * Source-compatible constructor for focused tests and direct callers that do not
     * need to override the dedicated long-running clients.
     */
    public AiServerProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration conceptPortfolioReadTimeout,
        Duration twinSurveyReadTimeout,
        String internalApiKey
    ) {
        this(
            baseUrl,
            connectTimeout,
            readTimeout,
            null,
            null,
            conceptPortfolioReadTimeout,
            twinSurveyReadTimeout,
            internalApiKey
        );
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
