package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.application.LegalReviewPolicy;
import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.config.AiProperties;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OpenAiLegalReviewAdapterTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void parsesExactlyTenTypedCategories() throws Exception {
        start(200, envelope(items()));
        var result = adapter(Duration.ofSeconds(1)).review(request());
        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.findings()).extracting(LegalReviewAiResponse.Finding::category)
            .containsExactly(LegalCategory.values());
    }

    @Test
    void malformedUnknownAndDuplicateCategoriesAreRejected() throws Exception {
        start(200, """
            {"id":"x","choices":[{"message":{"content":"not-json"}}]}
            """);
        assertInvalid();
        stop();
        var unknown = items();
        unknown.get(0).put("category", "NOT_A_CATEGORY");
        start(200, envelope(unknown));
        assertInvalid();
        stop();
        var duplicate = items();
        duplicate.get(1).put("category", duplicate.get(0).get("category"));
        start(200, envelope(duplicate));
        assertInvalid();
    }

    @Test
    void rateLimitAndServerFailureAreRetryableWithoutSecretLeak() throws Exception {
        start(429, "{}");
        assertRetryable("LEGAL_AI_HTTP_429");
        stop();
        start(503, "{}");
        assertRetryable("LEGAL_AI_HTTP_503");
    }

    @Test
    void readTimeoutIsRetryable() throws Exception {
        String body = envelope(items());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(400);
                respond(exchange, 200, body);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        assertThatThrownBy(() -> adapter(Duration.ofMillis(80)).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.isRetryable()).isTrue();
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_AI_NETWORK_TIMEOUT");
                assertThat(error.getMessage()).doesNotContain("super-secret-key");
            });
    }

    private void assertInvalid() {
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(1)).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_AI_RESPONSE_INVALID");
                assertThat(error.isRetryable()).isFalse();
            });
    }

    private void assertRetryable(String code) {
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(1)).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.isRetryable()).isTrue();
                assertThat(error.getErrorCode()).isEqualTo(code);
                assertThat(error.getMessage()).doesNotContain("super-secret-key");
            });
    }

    private OpenAiLegalReviewAdapter adapter(Duration timeout) {
        return new OpenAiLegalReviewAdapter(new AiProperties(
            true, "http://127.0.0.1:" + server.getAddress().getPort() + "/",
            "test-model", "super-secret-key", Duration.ofSeconds(1), timeout,
            0, 200000, 1048576), mapper);
    }

    private LegalReviewAiRequest request() {
        return new LegalReviewAiRequest(
            1L, 2L, 3L, LegalReviewPolicy.PROMPT_VERSION, LegalReviewPolicy.PROMPT,
            List.of(new LegalReviewAiRequest.Section("LEGAL_PERMITS", "법률", "내용", "[]")));
    }

    private List<Map<String, Object>> items() {
        List<Map<String, Object>> values = new ArrayList<>();
        for (LegalCategory category : LegalCategory.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category.name());
            item.put("applicability", "POSSIBLY_APPLICABLE");
            item.put("riskLevel", "MEDIUM");
            item.put("title", category.name());
            item.put("finding", "확인 필요");
            item.put("rationale", "정보에 따라 달라질 수 있음");
            item.put("recommendedAction", "전문가 확인");
            item.put("evidence", List.of("입력 근거"));
            item.put("sourceSectionCodes", List.of("LEGAL_PERMITS"));
            item.put("requiresProfessionalReview", false);
            item.put("confidence", 0.7);
            values.add(item);
        }
        return values;
    }

    private String envelope(List<Map<String, Object>> findings) throws Exception {
        String content = mapper.writeValueAsString(Map.of(
            "overallRiskLevel", "MEDIUM",
            "summary", "사전 확인 결과",
            "findings", findings,
            "questions", List.of()
        ));
        return mapper.writeValueAsString(Map.of(
            "id", "legal-provider-1",
            "choices", List.of(Map.of("message", Map.of("content", content)))
        ));
    }

    private void start(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, status, body));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
