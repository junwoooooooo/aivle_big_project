package com.aivle.backend.integration.ai.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.DimensionCode;
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
import java.util.*;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class OpenAiFeasibilityAnalysisAdapter implements FeasibilityAnalysisAiClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiFeasibilityAnalysisAdapter(AiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createClient(properties));
    }

    OpenAiFeasibilityAnalysisAdapter(
        AiProperties properties, ObjectMapper objectMapper, RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public FeasibilityAnalysisAiResponse analyze(FeasibilityAnalysisAiRequest request) {
        validateConfiguration();
        try {
            String input = objectMapper.writeValueAsString(request);
            if (input.length() > properties.maxInputCharacters()) {
                throw failure("FEASIBILITY_AI_INPUT_TOO_LARGE", false, null);
            }
            String body = restClient.post()
                .uri(properties.baseUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ChatRequest(properties.model(), new ResponseFormat("json_object"),
                    List.of(new Message("system", request.promptText()),
                        new Message("user", input))))
                .retrieve().body(String.class);
            return parse(body);
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            boolean retryable = status.value() == 408 || status.value() == 429
                || status.is5xxServerError();
            throw failure("FEASIBILITY_AI_HTTP_" + status.value(), retryable, exception);
        } catch (ResourceAccessException exception) {
            throw failure("FEASIBILITY_AI_NETWORK_TIMEOUT", true, exception);
        } catch (AiClientException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw failure("FEASIBILITY_AI_RESPONSE_INVALID", false, exception);
        }
    }

    private FeasibilityAnalysisAiResponse parse(String body) throws JacksonException {
        if (body == null || body.isBlank()
            || body.getBytes(StandardCharsets.UTF_8).length > properties.maxResponseBytes()) {
            throw failure("FEASIBILITY_AI_RESPONSE_INVALID", false, null);
        }
        ChatResponse transport = objectMapper.readValue(body, ChatResponse.class);
        if (transport.choices() == null || transport.choices().isEmpty()
            || transport.choices().get(0) == null
            || transport.choices().get(0).message() == null
            || transport.choices().get(0).message().content() == null) {
            throw failure("FEASIBILITY_AI_RESPONSE_INVALID", false, null);
        }
        FeasibilityAnalysisAiResponse raw = objectMapper.readValue(
            transport.choices().get(0).message().content(),
            FeasibilityAnalysisAiResponse.class);
        validate(raw);
        return new FeasibilityAnalysisAiResponse(
            "openai", properties.model(), transport.id(), raw.summary(),
            raw.keyStrengths(), raw.keyRisks(), raw.dimensions(), raw.validationTasks());
    }

    private void validate(FeasibilityAnalysisAiResponse result) {
        if (result == null || result.summary() == null || result.keyStrengths() == null
            || result.keyRisks() == null || result.dimensions() == null
            || result.dimensions().size() != DimensionCode.values().length
            || result.validationTasks() == null) {
            throw failure("FEASIBILITY_AI_RESPONSE_INVALID", false, null);
        }
        EnumSet<DimensionCode> seen = EnumSet.noneOf(DimensionCode.class);
        Set<String> taskCodes = new HashSet<>();
        for (var item : result.dimensions()) {
            if (item == null || item.code() == null || item.confidence() == null
                || item.status() == null || item.finding() == null || item.rationale() == null
                || item.strengths() == null || item.risks() == null
                || item.assumptions() == null || item.evidence() == null
                || item.sourceSectionCodes() == null || item.legalFindingIds() == null
                || item.recommendedActions() == null || !seen.add(item.code())
                || (item.score() != null && (item.score() < 0 || item.score() > 100))) {
                throw failure("FEASIBILITY_AI_RESPONSE_INVALID", false, null);
            }
        }
        for (var task : result.validationTasks()) {
            if (task == null || task.code() == null || task.code().isBlank()
                || task.dimensionCode() == null || task.priority() == null
                || !taskCodes.add(task.code())) {
                throw failure("FEASIBILITY_AI_RESPONSE_INVALID", false, null);
            }
        }
    }

    private void validateConfiguration() {
        if (blank(properties.baseUrl()) || blank(properties.model()) || blank(properties.apiKey())) {
            throw failure("AI_CONFIGURATION_INVALID", false, null);
        }
    }

    private AiClientException failure(String code, boolean retryable, Throwable cause) {
        return new AiClientException(code,
            retryable ? "AI 서비스가 일시적으로 응답하지 않아 다시 시도합니다."
                : "사업 타당성 AI 결과를 안전하게 처리할 수 없습니다.",
            retryable, null, cause);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

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
}
