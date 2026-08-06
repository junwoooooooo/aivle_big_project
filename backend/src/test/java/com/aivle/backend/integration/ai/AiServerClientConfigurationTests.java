package com.aivle.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;

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

    @Test
    void configuredReadTimeoutIsApplied() throws Exception {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0),
            0
        );
        server.createContext("/health", exchange -> {
            try {
                Thread.sleep(300);
                byte[] body = """
                    {"status":"ok","service":"ai-server","request_id":"x"}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            AiServerProperties properties = new AiServerProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofMillis(50),
                ""
            );
            AiServerClientSupport support =
                new AiServerClientSupport(
                    properties,
                    new ObjectMapper()
                );
            AiServerHealthClient client = new AiServerHealthClient(
                new AiServerClientConfiguration()
                    .createRestClient(properties),
                support
            );

            AiServerException exception = assertThrows(
                AiServerException.class,
                () -> client.checkHealth("timeout-request")
            );
            assertEquals(
                "AI_SERVER_TIMEOUT",
                exception.getErrorCode()
            );
            assertTrue(exception.isRetryable());
        } finally {
            server.stop(0);
        }
    }
}
