package com.aivle.backend.integration.ai.openai;

import com.aivle.backend.config.AiProperties;
import com.aivle.backend.document.structure.BusinessPlanSectionCatalog;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.document.structure.StructuredItemStatus;
import com.aivle.backend.integration.ai.document.*;
import com.aivle.backend.integration.ai.prompt.DocumentStructurePromptFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiDocumentStructureAdapterTests {
    private static final Duration LOCAL_SERVER_READ_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessPlanSectionCatalog catalog = new BusinessPlanSectionCatalog();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesTypedTwelveItemResponse() throws Exception {
        start(200, Map.of(), envelope(items()));

        DocumentStructureAiResponse response = adapter(LOCAL_SERVER_READ_TIMEOUT)
            .structureDocument(request());

        assertThat(response.providerRequestId()).isEqualTo("provider-request-1");
        assertThat(response.result().items()).hasSize(12);
        assertThat(response.result().items())
            .allMatch(item -> item.status() == StructuredItemStatus.PRESENT);
        assertThat(response.result().rawResultHash()).hasSize(64);
    }

    @Test
    void acceptsExplicitMissingAndPartialStatuses() throws Exception {
        List<TestItem> items = items();
        items.set(0, items.get(0).withStatus("MISSING"));
        items.set(1, items.get(1).withStatus("PARTIAL"));
        start(200, Map.of(), envelope(items));

        var result = adapter(LOCAL_SERVER_READ_TIMEOUT).structureDocument(request()).result();
        assertThat(result.items().get(0).status()).isEqualTo(StructuredItemStatus.MISSING);
        assertThat(result.items().get(1).status()).isEqualTo(StructuredItemStatus.PARTIAL);
    }

    @Test
    void malformedJsonIsNonRetryable() throws Exception {
        start(200, Map.of(), """
            {"id":"provider-request-1","choices":[{"message":{"content":"not-json"}}]}
            """);
        assertInvalidResponse();
    }

    @Test
    void missingRequiredItemIsRejected() throws Exception {
        List<TestItem> items = items();
        items.remove(items.size() - 1);
        start(200, Map.of(), envelope(items));
        assertInvalidResponse();
    }

    @Test
    void unknownSectionCodeIsRejected() throws Exception {
        List<TestItem> items = items();
        items.set(0, items.get(0).withCode("UNKNOWN_CODE"));
        start(200, Map.of(), envelope(items));
        assertInvalidResponse();
    }

    @Test
    void duplicateSectionCodeIsRejected() throws Exception {
        List<TestItem> items = items();
        items.set(1, items.get(1).withCode(items.get(0).sectionCode()));
        start(200, Map.of(), envelope(items));
        assertInvalidResponse();
    }

    @Test
    void unauthorizedIsNonRetryableAndDoesNotExposeSecret() throws Exception {
        start(401, Map.of(), "{}");
        assertThatThrownBy(() -> adapter(LOCAL_SERVER_READ_TIMEOUT).structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(failure -> {
                AiClientException exception = (AiClientException) failure;
                assertThat(exception.isRetryable()).isFalse();
                assertThat(exception.getErrorCode()).isEqualTo("AI_HTTP_401");
                assertThat(exception.getMessage()).doesNotContain("super-secret-key");
            });
    }

    @Test
    void rateLimitIsRetryableAndParsesRetryAfter() throws Exception {
        start(429, Map.of("Retry-After", "17"), "{}");
        assertThatThrownBy(() -> adapter(LOCAL_SERVER_READ_TIMEOUT).structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(failure -> {
                AiClientException exception = (AiClientException) failure;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(17));
            });
    }

    @Test
    void serviceUnavailableIsRetryable() throws Exception {
        start(503, Map.of(), "{}");
        assertThatThrownBy(() -> adapter(LOCAL_SERVER_READ_TIMEOUT).structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .extracting("retryable")
            .isEqualTo(true);
    }

    @Test
    void readTimeoutIsRetryable() throws Exception {
        String responseBody = envelope(items());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(500);
                respond(exchange, 200, Map.of(), responseBody);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        assertThatThrownBy(() -> adapter(Duration.ofMillis(100)).structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(failure -> {
                AiClientException exception = (AiClientException) failure;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getErrorCode()).isEqualTo("AI_NETWORK_TIMEOUT");
            });
    }

    @Test
    void connectTimeoutIsRetryableWithoutExternalNetwork() {
        var restClient = mock(org.springframework.web.client.RestClient.class);
        var uriSpec = mock(org.springframework.web.client.RestClient.RequestBodyUriSpec.class);
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        when(uriSpec.header(anyString(), any(String[].class))).thenReturn(uriSpec);
        when(uriSpec.body(any(OpenAiTransportDtos.ChatRequest.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenThrow(new org.springframework.web.client.ResourceAccessException(
            "connect timed out",
            new HttpConnectTimeoutException("connect timed out")
        ));
        AiProperties properties = new AiProperties(
            true, "http://127.0.0.1:1/", "test-model", "super-secret-key",
            Duration.ofMillis(100), Duration.ofSeconds(1), 0, 200000, 1048576
        );
        OpenAiDocumentStructureAdapter adapter = new OpenAiDocumentStructureAdapter(
            properties, catalog, objectMapper, restClient
        );

        assertThatThrownBy(() -> adapter.structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(failure -> {
                AiClientException exception = (AiClientException) failure;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getErrorCode()).isEqualTo("AI_NETWORK_TIMEOUT");
                assertThat(exception.getMessage()).doesNotContain("super-secret-key");
            });
    }

    @Test
    void missingConfigurationFailsOnlyAtExecution() {
        AiProperties properties = new AiProperties(
            true, "", "", "", Duration.ofSeconds(1), Duration.ofSeconds(1),
            0, 200000, 1048576
        );
        OpenAiDocumentStructureAdapter adapter =
            new OpenAiDocumentStructureAdapter(properties, catalog, objectMapper);

        assertThatThrownBy(() -> adapter.structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .extracting("errorCode")
            .isEqualTo("AI_CONFIGURATION_INVALID");
    }

    private void assertInvalidResponse() {
        assertThatThrownBy(() -> adapter(LOCAL_SERVER_READ_TIMEOUT).structureDocument(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(failure -> {
                AiClientException exception = (AiClientException) failure;
                assertThat(exception.getErrorCode()).isEqualTo("AI_RESPONSE_INVALID");
                assertThat(exception.isRetryable()).isFalse();
            });
    }

    private OpenAiDocumentStructureAdapter adapter(Duration readTimeout) {
        AiProperties properties = new AiProperties(
            true,
            "http://127.0.0.1:" + server.getAddress().getPort() + "/",
            "test-model",
            "super-secret-key",
            Duration.ofSeconds(1),
            readTimeout,
            0,
            200000,
            1048576
        );
        return new OpenAiDocumentStructureAdapter(properties, catalog, objectMapper);
    }

    private DocumentStructureAiRequest request() {
        var prompt = new DocumentStructurePromptFactory(catalog).current();
        return new DocumentStructureAiRequest(
            1L,
            2L,
            3L,
            "docx",
            "1",
            "plan.docx",
            List.of(new DocumentStructureBlock(0, "PARAGRAPH", "사업 내용", "body[0]",
                null, null, null)),
            catalog.all().stream()
                .map(definition -> new DocumentStructureSection(
                    definition.code().name(),
                    definition.displayName(),
                    definition.description(),
                    definition.required(),
                    definition.allowedMissingPolicy().name(),
                    definition.aliases()
                ))
                .toList(),
            prompt.catalogVersion(),
            prompt.version(),
            prompt.template(),
            "request-hash"
        );
    }

    private List<TestItem> items() {
        List<TestItem> items = new ArrayList<>();
        for (var definition : catalog.all()) {
            items.add(new TestItem(
                definition.code().name(),
                definition.displayName(),
                "PRESENT",
                "문서에서 확인된 내용",
                "",
                new BigDecimal("0.9"),
                List.of("근거"),
                List.of(0)
            ));
        }
        return items;
    }

    private String envelope(List<TestItem> items) throws Exception {
        String content = objectMapper.writeValueAsString(new TestStructuredResponse(items));
        return objectMapper.writeValueAsString(new TestChatResponse(
            "provider-request-1",
            List.of(new TestChoice(new TestMessage(content)))
        ));
    }

    private void start(int status, Map<String, String> headers, String body)
        throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, status, headers, body));
        server.start();
    }

    private void respond(
        HttpExchange exchange,
        int status,
        Map<String, String> headers,
        String body
    ) throws IOException {
        exchange.getRequestBody().readAllBytes();
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record TestChatResponse(String id, List<TestChoice> choices) {
    }

    private record TestChoice(TestMessage message) {
    }

    private record TestMessage(String content) {
    }

    private record TestStructuredResponse(List<TestItem> items) {
    }

    private record TestItem(
        String sectionCode,
        String sectionName,
        String status,
        String extractedContent,
        String reason,
        BigDecimal confidence,
        List<String> evidence,
        List<Integer> sourceBlockReferences
    ) {
        private TestItem withStatus(String value) {
            return new TestItem(sectionCode, sectionName, value, extractedContent, reason,
                confidence, evidence, sourceBlockReferences);
        }

        private TestItem withCode(String value) {
            return new TestItem(value, sectionName, status, extractedContent, reason,
                confidence, evidence, sourceBlockReferences);
        }
    }
}
