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
        assertTrue(properties.hasInternalApiKey());
    }

}
