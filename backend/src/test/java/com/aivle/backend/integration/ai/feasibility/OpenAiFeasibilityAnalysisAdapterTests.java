package com.aivle.backend.integration.ai.feasibility;

import com.aivle.backend.analysis.feasibility.*;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.RiskLevel;
import com.aivle.backend.config.AiProperties;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OpenAiFeasibilityAnalysisAdapterTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void parsesExactlyTenTypedDimensions() throws Exception {
        start(200, envelope(validResult()));
        var result = adapter().analyze(request());
        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.dimensions()).extracting(
            FeasibilityAnalysisAiResponse.Dimension::code).containsExactly(DimensionCode.values());
    }

    @Test
    void duplicateDimensionAndOutOfRangeScoreAreRejected() throws Exception {
        var duplicate = validResult();
        var list = new ArrayList<>(duplicate.dimensions());
        list.set(1, list.get(0));
        start(200, envelope(new FeasibilityAnalysisAiResponse(
            null, null, null, duplicate.summary(), duplicate.keyStrengths(),
            duplicate.keyRisks(), list, duplicate.validationTasks())));
        assertInvalid();
        stop();

        var invalidScore = new ArrayList<>(validResult().dimensions());
        var first = invalidScore.get(0);
        invalidScore.set(0, new FeasibilityAnalysisAiResponse.Dimension(
            first.code(), 101, first.confidence(), first.status(), first.finding(),
            first.rationale(), first.strengths(), first.risks(), first.assumptions(),
            first.evidence(), first.sourceSectionCodes(), first.legalFindingIds(),
            first.recommendedActions()));
        start(200, envelope(new FeasibilityAnalysisAiResponse(
            null, null, null, "summary", List.of(), List.of(), invalidScore, List.of())));
        assertInvalid();
    }

    @Test
    void rateLimitIsRetryableAndDoesNotLeakSecret() throws Exception {
        start(429, "{}");
        assertThatThrownBy(() -> adapter().analyze(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.isRetryable()).isTrue();
                assertThat(error.getErrorCode()).isEqualTo("FEASIBILITY_AI_HTTP_429");
                assertThat(error.getMessage()).doesNotContain("super-secret-key");
            });
    }

    private void assertInvalid() {
        assertThatThrownBy(() -> adapter().analyze(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.isRetryable()).isFalse();
                assertThat(error.getErrorCode()).isEqualTo("FEASIBILITY_AI_RESPONSE_INVALID");
            });
    }

    private OpenAiFeasibilityAnalysisAdapter adapter() {
        return new OpenAiFeasibilityAnalysisAdapter(new AiProperties(
            true, "http://127.0.0.1:" + server.getAddress().getPort() + "/",
            "test-model", "super-secret-key", Duration.ofSeconds(1), Duration.ofSeconds(1),
            0, 200000, 1048576), mapper);
    }

    private FeasibilityAnalysisAiRequest request() {
        var catalog = FeasibilityDimensionCatalog.all().stream().map(item ->
            new FeasibilityAnalysisAiRequest.CatalogDimension(
                item.code(), item.displayName(), item.displayOrder(), item.description(),
                item.sourceSections().stream().map(Enum::name).toList())).toList();
        return new FeasibilityAnalysisAiRequest(
            1L, 2L, 3L, 4L, FeasibilityPolicy.PROMPT_VERSION,
            FeasibilityDimensionCatalog.VERSION, FeasibilityPolicy.PROMPT, catalog,
            List.of(), List.of(), new FeasibilityAnalysisAiRequest.LegalContext(
                3L, "COMPLETED", RiskLevel.LOW, "summary", List.of(), List.of()));
    }

    private FeasibilityAnalysisAiResponse validResult() {
        var dimensions = Arrays.stream(DimensionCode.values()).map(code ->
            new FeasibilityAnalysisAiResponse.Dimension(
                code, 70, Confidence.MEDIUM, DimensionStatus.ASSESSED,
                "finding", "rationale", List.of("strength"), List.of("risk"),
                List.of(), List.of(new FeasibilityAnalysisAiResponse.Evidence(
                    EvidenceType.DOCUMENT_FACT, "document", "section")),
                List.of(), List.of(), List.of("action"))).toList();
        return new FeasibilityAnalysisAiResponse(
            null, null, null, "summary", List.of("strength"), List.of("risk"),
            dimensions, List.of());
    }

    private String envelope(FeasibilityAnalysisAiResponse result) throws Exception {
        return mapper.writeValueAsString(Map.of(
            "id", "provider-1",
            "choices", List.of(Map.of("message", Map.of(
                "content", mapper.writeValueAsString(result))))));
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
