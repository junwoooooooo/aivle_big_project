package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.config.AiProperties;
import com.aivle.backend.integration.ai.document.AiClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.EnumSet;
import com.aivle.backend.analysis.legal.entity.LegalCategory;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class OpenAiLegalReviewAdapter implements LegalReviewAiClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiLegalReviewAdapter(AiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createClient(properties));
    }

    OpenAiLegalReviewAdapter(
        AiProperties properties, ObjectMapper objectMapper, RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public LegalReviewAiResponse review(LegalReviewAiRequest request) {
        validateConfiguration();
        try {
            String body = restClient.post()
                .uri(properties.baseUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ChatRequest(
                    properties.model(),
                    new ResponseFormat("json_object"),
                    List.of(
                        new Message("system", request.promptText()),
                        new Message("user", objectMapper.writeValueAsString(new Input(
                            request.projectId(), request.structuredPlanId(),
                            request.sourceDocumentVersionId(), request.promptVersion(),
                            request.sections())))
                    )))
                .retrieve()
                .body(String.class);
            return parse(body);
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(
                "LEGAL_AI_NETWORK_TIMEOUT", "AI 사전검토 응답이 지연되어 다시 시도합니다.",
                true, null, exception);
        } catch (AiClientException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private LegalReviewAiResponse parse(String body) throws JacksonException {
        if (body == null || body.isBlank()
            || body.getBytes(StandardCharsets.UTF_8).length > properties.maxResponseBytes()) {
            throw invalid(null);
        }
        ChatResponse transport = objectMapper.readValue(body, ChatResponse.class);
        if (transport.choices() == null || transport.choices().isEmpty()
            || transport.choices().get(0) == null
            || transport.choices().get(0).message() == null
            || transport.choices().get(0).message().content() == null) {
            throw invalid(null);
        }
        LegalReviewAiResponse result = objectMapper.readValue(
            transport.choices().get(0).message().content(), LegalReviewAiResponse.class);
        validateResult(result);
        return new LegalReviewAiResponse(
            "openai", properties.model(), transport.id(), result.overallRiskLevel(),
            result.summary(), result.findings(), result.questions());
    }

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
        if (blank(properties.baseUrl()) || blank(properties.model()) || blank(properties.apiKey())) {
            throw new AiClientException(
                "AI_CONFIGURATION_INVALID", "AI 서비스 설정이 완료되지 않았습니다.",
                false, null, null);
        }
    }

    private AiClientException httpFailure(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        boolean retryable = status.value() == 408 || status.value() == 429
            || status.is5xxServerError();
        return new AiClientException(
            "LEGAL_AI_HTTP_" + status.value(),
            retryable ? "AI 서비스가 일시적으로 응답하지 않아 다시 시도합니다."
                : "AI 사전검토 요청을 처리할 수 없습니다.",
            retryable, null, exception);
    }

    private AiClientException invalid(Throwable cause) {
        return new AiClientException(
            "LEGAL_AI_RESPONSE_INVALID", "AI 사전검토 결과 형식이 올바르지 않습니다.",
            false, null, cause);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static RestClient createClient(AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    record ChatRequest(String model, ResponseFormat response_format, List<Message> messages) {}
    record ResponseFormat(String type) {}
    record Message(String role, String content) {}
    record ChatResponse(String id, List<Choice> choices) {}
    record Choice(ResponseMessage message) {}
    record ResponseMessage(String content) {}
    record Input(
        Long projectId, Long structuredPlanId, Long sourceDocumentVersionId,
        String promptVersion, List<LegalReviewAiRequest.Section> sections
    ) {}
}
