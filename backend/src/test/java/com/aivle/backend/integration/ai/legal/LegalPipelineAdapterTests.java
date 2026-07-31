package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.application.LegalReviewPolicy;
import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.config.LegalServiceProperties;
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

class LegalPipelineAdapterTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void parsesExactlyTenTypedCategoriesAndStampsProvider() throws Exception {
        start(200, envelope(items()));
        var result = adapter(Duration.ofSeconds(1)).review(request());
        assertThat(result.provider()).isEqualTo("legal-pipeline");
        assertThat(result.model()).isEqualTo("claude-sonnet-5+law.go.kr");
        assertThat(result.providerRequestId()).isEqualTo("legal-run-1");
        assertThat(result.findings()).extracting(LegalReviewAiResponse.Finding::category)
            .containsExactly(LegalCategory.values());
        // 문자열 근거(구 형식)는 조문명만이라도 살려 수용한다
        assertThat(result.findings().get(0).evidence()).singleElement()
            .satisfies(item -> {
                assertThat(item.lawName()).isEqualTo("전자상거래법");
                assertThat(item.article()).isEqualTo("제12조");
                assertThat(item.plainSummary()).isNull();
            });
        assertThat(result.findings().get(0).reasoning()).isNull();
    }

    @Test
    void parsesStructuredEvidenceAndReasoningChain() throws Exception {
        List<Map<String, Object>> items = items();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("lawName", "전자상거래 등에서의 소비자보호에 관한 법률");
        evidence.put("article", "제12조");
        evidence.put("title", "통신판매업자의 신고 등");
        evidence.put("role", "REQUIREMENT");
        evidence.put("plainSummary", "온라인으로 물건을 팔려면 관할 구청에 신고해야 합니다.");
        evidence.put("whyRelevant", "자사몰 직접 판매라 신고 의무가 적용됨");
        evidence.put("excerpt", "제12조(통신판매업자의 신고 등) ① …");
        evidence.put("effectiveDate", "2026-07-21");
        evidence.put("lawUrl", "https://www.law.go.kr/법령/전자상거래등에서의소비자보호에관한법률");
        items.get(0).put("evidence", List.of(evidence));
        items.get(0).put("reasoning", Map.of(
            "planBasis", Map.of("sectionLabels", List.of("판매·유통"), "quotes", List.of("자사몰 판매")),
            "regulatoryPath", Map.of("topic", "전자상거래·통신판매", "status", "해당",
                "reason", "비대면 판매가 확인됨"),
            "obligations", List.of(Map.of("article", "제12조",
                "lawName", "전자상거래 등에서의 소비자보호에 관한 법률", "summary", "통신판매업 신고")),
            "consequence", Map.of("sanctionArticles", List.of("제42조"),
                "text", "신고 없이 판매하면 벌칙 대상이 될 수 있음"),
            "conclusion", Map.of("action", "통신판매업 신고", "timing", "판매 개시 전")));

        start(200, envelope(items));
        var finding = adapter(Duration.ofSeconds(1)).review(request()).findings().get(0);

        assertThat(finding.evidence()).singleElement().satisfies(item -> {
            assertThat(item.role()).isEqualTo(LegalReviewAiResponse.EvidenceRole.REQUIREMENT);
            assertThat(item.plainSummary()).isEqualTo("온라인으로 물건을 팔려면 관할 구청에 신고해야 합니다.");
            assertThat(item.whyRelevant()).isEqualTo("자사몰 직접 판매라 신고 의무가 적용됨");
            assertThat(item.excerpt()).startsWith("제12조(통신판매업자의 신고 등)");
        });
        var chain = finding.reasoning();
        assertThat(chain.regulatoryPath().topic()).isEqualTo("전자상거래·통신판매");
        assertThat(chain.obligations()).singleElement()
            .extracting(LegalReviewAiResponse.Reasoning.Obligation::article).isEqualTo("제12조");
        assertThat(chain.consequence().sanctionArticles()).containsExactly("제42조");
        assertThat(chain.conclusion().timing()).isEqualTo("판매 개시 전");
    }

    @Test
    void sendsPlainJsonObjectBodyWithReadableKoreanSections() throws Exception {
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var upgrade = new java.util.concurrent.atomic.AtomicReference<String>();
        String body = envelope(items());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            respond(exchange, 200, body);
        });
        server.start();

        adapter(Duration.ofSeconds(2)).review(request());

        // HTTP/1.1 전용 서버(uvicorn 등)에 h2c 업그레이드를 시도하면 본문이 유실된다.
        assertThat(upgrade.get()).isNull();
        assertThat(received.get()).isNotBlank();

        // 서비스는 JSON 객체를 기대한다. byte[] 본문이 base64 문자열로 직렬화되면 여기서 걸린다.
        var sent = mapper.readTree(received.get());
        assertThat(sent.isObject()).isTrue();
        assertThat(sent.get("projectId").asLong()).isEqualTo(1L);
        assertThat(sent.get("structuredPlanId").asLong()).isEqualTo(2L);
        assertThat(sent.get("sections").isArray()).isTrue();
        assertThat(sent.get("sections").get(0).get("code").asString()).isEqualTo("LEGAL_PERMITS");
        assertThat(sent.get("sections").get(0).get("content").asString()).isEqualTo("한글 본문");
    }

    @Test
    void incompleteUnknownAndDuplicateCategoriesAreRejected() throws Exception {
        var missingOne = items();
        missingOne.remove(0);
        start(200, envelope(missingOne));
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
    void oversizedResponseIsNonRetryable() throws Exception {
        start(200, envelope(items()));
        var properties = new LegalServiceProperties(
            "pipeline", baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1), 200000, 32);
        assertThatThrownBy(() -> new LegalPipelineAdapter(properties, mapper).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_PIPELINE_RESPONSE_INVALID");
                assertThat(error.isRetryable()).isFalse();
            });
    }

    @Test
    void oversizedInputIsRejectedBeforeCalling() throws Exception {
        start(200, envelope(items()));
        var properties = new LegalServiceProperties(
            "pipeline", baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 1048576);
        assertThatThrownBy(() -> new LegalPipelineAdapter(properties, mapper).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_PIPELINE_INPUT_TOO_LARGE");
                assertThat(error.isRetryable()).isFalse();
            });
    }

    @Test
    void serviceUnavailableIsRetryableAndUnprocessableIsNot() throws Exception {
        start(503, "{}");
        assertRetryable("LEGAL_PIPELINE_HTTP_503");
        stop();
        start(422, "{\"detail\":\"routing failed\"}");
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(1)).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_PIPELINE_HTTP_422");
                assertThat(error.isRetryable()).isFalse();
            });
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
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_PIPELINE_NETWORK_TIMEOUT");
            });
    }

    @Test
    void blankBaseUrlIsConfigurationFailure() {
        var properties = new LegalServiceProperties(
            "pipeline", "  ", Duration.ofSeconds(1), Duration.ofSeconds(1), 200000, 1048576);
        assertThatThrownBy(() -> new LegalPipelineAdapter(properties, mapper).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_SERVICE_CONFIGURATION_INVALID");
                assertThat(error.isRetryable()).isFalse();
            });
    }

    private void assertInvalid() {
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(1)).review(request()))
            .isInstanceOf(AiClientException.class)
            .satisfies(value -> {
                var error = (AiClientException) value;
                assertThat(error.getErrorCode()).isEqualTo("LEGAL_PIPELINE_RESPONSE_INVALID");
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
            });
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private LegalPipelineAdapter adapter(Duration timeout) {
        return new LegalPipelineAdapter(new LegalServiceProperties(
            "pipeline", baseUrl(), Duration.ofSeconds(1), timeout, 200000, 1048576), mapper);
    }

    private LegalReviewAiRequest request() {
        return new LegalReviewAiRequest(
            1L, 2L, 3L, LegalReviewPolicy.PROMPT_VERSION, LegalReviewPolicy.PROMPT,
            List.of(new LegalReviewAiRequest.Section("LEGAL_PERMITS", "법률", "한글 본문", "[]")));
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
            item.put("evidence", List.of("전자상거래법 제12조 (시행 2024-01-01, MST 268659)"));
            item.put("sourceSectionCodes", List.of("LEGAL_PERMITS"));
            item.put("requiresProfessionalReview", true);
            item.put("confidence", 0.7);
            values.add(item);
        }
        return values;
    }

    private String envelope(List<Map<String, Object>> findings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", "legal-pipeline");
        body.put("model", "claude-sonnet-5+law.go.kr");
        body.put("providerRequestId", "legal-run-1");
        body.put("overallRiskLevel", "MEDIUM");
        body.put("summary", "사전 확인 결과");
        body.put("findings", findings);
        body.put("questions", List.of());
        return mapper.writeValueAsString(body);
    }

    private void start(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, status, body));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // FastAPI JSONResponse와 동일하게 charset을 붙이지 않는다 — 어댑터가 UTF-8로 읽어야 한다.
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
