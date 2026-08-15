package com.aivle.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;

class AiServerClientConfigurationTests {

    @Test
    void bindsAiServerProperties() {
        MapPropertySource source = new MapPropertySource(
            "test",
            Map.of(
                "app.ai-server.base-url", "http://localhost:9999/",
                "app.ai-server.connect-timeout", "250ms",
                "app.ai-server.read-timeout", "2s",
                "app.ai-server.long-read-timeout", "7m",
                "app.ai-server.market-research-read-timeout", "22m",
                "app.ai-server.concept-portfolio-read-timeout", "15m",
                "app.ai-server.twin-survey-read-timeout", "14m",
                "app.ai-server.internal-api-key", "secret"
            )
        );
        Binder binder = new Binder(
            ConfigurationPropertySources.from(source)
        );

        AiServerProperties properties = binder.bind(
            "app.ai-server",
            Bindable.of(AiServerProperties.class)
        ).orElseThrow(() ->
            new AssertionError("AI server properties did not bind")
        );

        assertEquals("http://localhost:9999", properties.baseUrl());
        assertEquals(
            Duration.ofMillis(250),
            properties.connectTimeout()
        );
        assertEquals(Duration.ofSeconds(2), properties.readTimeout());
        assertEquals(Duration.ofMinutes(7), properties.longReadTimeout());
        assertEquals(Duration.ofMinutes(22), properties.marketResearchReadTimeout());
        assertEquals(Duration.ofMinutes(15), properties.conceptPortfolioReadTimeout());
        assertEquals(Duration.ofMinutes(14), properties.twinSurveyReadTimeout());
        assertTrue(properties.hasInternalApiKey());
    }

    @Test
    void rejectsNonPositiveSpecializedTimeouts() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new AiServerProperties("http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofMinutes(15), Duration.ZERO, "secret"));
    }

    @Test
    void allDefaultTimeoutsArePositiveAndTwinDefaultIsFourteenMinutes() {
        AiServerProperties properties = new AiServerProperties(null, null, null, null, null, null);
        assertTrue(!properties.connectTimeout().isZero() && !properties.connectTimeout().isNegative());
        assertTrue(!properties.readTimeout().isZero() && !properties.readTimeout().isNegative());
        assertEquals(Duration.ofMinutes(7), properties.longReadTimeout());
        assertEquals(Duration.ofMinutes(22), properties.marketResearchReadTimeout());
        assertTrue(!properties.conceptPortfolioReadTimeout().isZero()
            && !properties.conceptPortfolioReadTimeout().isNegative());
        assertTrue(!properties.twinSurveyReadTimeout().isZero()
            && !properties.twinSurveyReadTimeout().isNegative());
        assertEquals(Duration.ofMinutes(14), properties.twinSurveyReadTimeout());
    }

}
