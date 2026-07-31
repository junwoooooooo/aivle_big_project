package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.config.LegalServiceProperties;
import com.aivle.backend.integration.ai.document.AiClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

/**
 * 법령 조사 파이프라인(FastAPI)을 호출하는 어댑터.
 * 법제처 국가법령정보 Open API로 현행 조문을 확정하고 27개 규제 경로를
 * 10개 {@link LegalCategory}로 집계한 결과를 돌려받는다.
 */
public class LegalPipelineAdapter implements LegalReviewAiClient {
    private static final String PROVIDER = "legal-pipeline";

    private final LegalServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LegalPipelineAdapter(LegalServiceProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createClient(properties));
    }

    LegalPipelineAdapter(
        LegalServiceProperties properties, ObjectMapper objectMapper, RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public LegalReviewAiResponse review(LegalReviewAiRequest request) {
        validateConfiguration();
        try {
            String input = objectMapper.writeValueAsString(new Input(
                request.projectId(), request.structuredPlanId(),
                request.sourceDocumentVersionId(), request.promptVersion(),
                request.sections()));
            if (input.length() > properties.maxInputCharacters()) {
                throw new AiClientException(
                    "LEGAL_PIPELINE_INPUT_TOO_LARGE", "사업계획 본문이 너무 커서 사전검토를 요청할 수 없습니다.",
                    false, null, null);
            }
            // 응답에 charset이 없으면 Spring이 ISO-8859-1로 디코딩해 법령 한글이 깨진다.
            // 법령명·조문은 결과의 핵심 근거이므로 바이트로 받아 UTF-8로 직접 읽는다.
            byte[] body = restClient.post()
                .uri(properties.baseUrl())
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .header(HttpHeaders.ACCEPT, "application/json")
                .body(input.getBytes(StandardCharsets.UTF_8))
                .retrieve()
                .body(byte[].class);
            return parse(body);
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(
                "LEGAL_PIPELINE_NETWORK_TIMEOUT", "법령 조사 서비스 응답이 지연되어 다시 시도합니다.",
                true, null, exception);
        } catch (AiClientException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private LegalReviewAiResponse parse(byte[] body) throws JacksonException {
        if (body == null || body.length == 0 || body.length > properties.maxResponseBytes()) {
            throw invalid(null);
        }
        LegalReviewAiResponse result = objectMapper.readValue(
            new String(body, StandardCharsets.UTF_8), LegalReviewAiResponse.class);
        validateResult(result);
        return new LegalReviewAiResponse(
            PROVIDER, result.model(), result.providerRequestId(), result.overallRiskLevel(),
            result.summary(), result.findings(), result.questions(), result.revisionRequests());
    }

    /**
     * 영속화 계층까지 가서 터지면 원인 파악이 어려우므로 10개 계약을 여기서 먼저 확인한다.
     */
    private void validateResult(LegalReviewAiResponse result) {
        if (result == null || result.overallRiskLevel() == null
            || result.summary() == null || result.findings() == null
            || result.findings().size() != LegalCategory.values().length
            || result.questions() == null) {
            throw invalid(null);
        }
        EnumSet<LegalCategory> categories = EnumSet.noneOf(LegalCategory.class);
        for (var finding : result.findings()) {
            if (finding == null || finding.category() == null
                || finding.applicability() == null || finding.riskLevel() == null
                || !categories.add(finding.category())) {
                throw invalid(null);
            }
        }
    }

    private void validateConfiguration() {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new AiClientException(
                "LEGAL_SERVICE_CONFIGURATION_INVALID", "법령 조사 서비스 설정이 완료되지 않았습니다.",
                false, null, null);
        }
    }

    private AiClientException httpFailure(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        boolean retryable = status.value() == 408 || status.value() == 429
            || status.is5xxServerError();
        return new AiClientException(
            "LEGAL_PIPELINE_HTTP_" + status.value(),
            retryable ? "법령 조사 서비스가 일시적으로 응답하지 않아 다시 시도합니다."
                : "법령 조사 요청을 처리할 수 없습니다.",
            retryable, null, exception);
    }

    private AiClientException invalid(Throwable cause) {
        return new AiClientException(
            "LEGAL_PIPELINE_RESPONSE_INVALID", "법령 조사 결과 형식이 올바르지 않습니다.",
            false, null, cause);
    }

    private static RestClient createClient(LegalServiceProperties properties) {
        // JDK HttpClient 기본값(HTTP/2)은 평문 http에서 h2c 업그레이드를 시도한다.
        // 법령 서비스(uvicorn/h11)는 HTTP/1.1 전용이라 업그레이드를 거부하고,
        // 그 과정에서 요청 본문이 유실돼 빈 body가 전달된다. 버전을 고정한다.
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    record Input(
        Long projectId, Long structuredPlanId, Long sourceDocumentVersionId,
        String promptVersion, List<LegalReviewAiRequest.Section> sections
    ) {}
}
