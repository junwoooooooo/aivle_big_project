package com.aivle.backend.integration.ai.persona;

import com.aivle.backend.config.AiProperties;
import com.aivle.backend.integration.ai.document.AiClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
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
public class OpenAiPersonaRecommendationAdapter implements PersonaRecommendationAiClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiPersonaRecommendationAdapter(
        AiProperties properties, ObjectMapper objectMapper
    ) {
        this(properties, objectMapper, createClient(properties));
    }

    OpenAiPersonaRecommendationAdapter(
        AiProperties properties, ObjectMapper objectMapper, RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public PersonaRecommendationAiResponse analyze(PersonaRecommendationAiRequest request) {
        validateConfiguration();
        try {
            String input = objectMapper.writeValueAsString(request);
            if (input.length() > properties.maxInputCharacters()) {
                throw failure("PERSONA_AI_INPUT_TOO_LARGE", false, null);
            }
            String body = restClient.post()
                .uri(properties.baseUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ChatRequest(properties.model(), new ResponseFormat("json_object"),
                    List.of(new Message("system", request.promptText()),
                        new Message("user", input))))
                .retrieve().body(String.class);
            return parse(request, body);
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            throw failure("PERSONA_AI_HTTP_" + status.value(),
                status.value() == 408 || status.value() == 429 || status.is5xxServerError(),
                exception);
        } catch (ResourceAccessException exception) {
            throw failure("PERSONA_AI_NETWORK_TIMEOUT", true, exception);
        } catch (AiClientException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw failure("PERSONA_AI_RESPONSE_INVALID", false, exception);
        }
    }

    private PersonaRecommendationAiResponse parse(
        PersonaRecommendationAiRequest request, String body
    ) throws JacksonException {
        if (body == null || body.isBlank()
            || body.getBytes(StandardCharsets.UTF_8).length > properties.maxResponseBytes()) {
            throw failure("PERSONA_AI_RESPONSE_INVALID", false, null);
        }
        ChatResponse transport = objectMapper.readValue(body, ChatResponse.class);
        if (transport.choices() == null || transport.choices().isEmpty()
            || transport.choices().get(0) == null
            || transport.choices().get(0).message() == null
            || transport.choices().get(0).message().content() == null) {
            throw failure("PERSONA_AI_RESPONSE_INVALID", false, null);
        }
        PersonaRecommendationAiResponse raw = objectMapper.readValue(
            transport.choices().get(0).message().content(),
            PersonaRecommendationAiResponse.class);
        validate(request, raw);
        return new PersonaRecommendationAiResponse(
            "openai", properties.model(), transport.id(), raw.summary(), raw.confidence(),
            raw.items(), raw.hypotheses(), raw.validationPlans());
    }

    private void validate(
        PersonaRecommendationAiRequest request, PersonaRecommendationAiResponse result
    ) {
        if (result == null || blank(result.summary()) || result.confidence() == null
            || result.items() == null || result.items().size() < 2
            || result.hypotheses() == null || result.validationPlans() == null) {
            throw failure("PERSONA_AI_RESPONSE_INVALID", false, null);
        }
        Set<String> allowed = new HashSet<>(request.personas().stream()
            .map(PersonaRecommendationAiRequest.BaselinePersona::personaCode).toList());
        Set<String> codes = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (var item : result.items()) {
            if (item == null || !allowed.contains(item.personaCode())
                || !codes.add(item.personaCode()) || item.rank() == null || item.rank() < 1
                || !ranks.add(item.rank()) || item.confidence() == null
                || item.fitScore() == null || item.fitScore() < 0 || item.fitScore() > 100
                || item.matchReasons() == null || item.mismatchRisks() == null
                || item.assumptions() == null || item.evidence() == null
                || item.verificationQuestions() == null || blank(item.interpretation())) {
                throw failure("PERSONA_AI_RESPONSE_INVALID", false, null);
            }
        }
        for (var hypothesis : result.hypotheses()) {
            if (hypothesis == null || !allowed.contains(hypothesis.personaCode())
                || hypothesis.type() == null || blank(hypothesis.statement())
                || blank(hypothesis.rationale()) || hypothesis.sourceType() == null
                || hypothesis.confidence() == null || hypothesis.priority() == null) {
                throw failure("PERSONA_AI_RESPONSE_INVALID", false, null);
            }
        }
        for (var plan : result.validationPlans()) {
            if (plan == null || !allowed.contains(plan.personaCode()) || plan.method() == null
                || blank(plan.objective()) || blank(plan.targetParticipantDescription())
                || plan.successCriteria() == null || plan.expectedEvidence() == null
                || plan.interviewQuestions() == null || plan.interviewQuestions().size() < 5
                || plan.interviewQuestions().size() > 10 || plan.surveyQuestions() == null
                || plan.linkedFeasibilityTaskIds() == null || plan.priority() == null
                || plan.interviewQuestions().stream().anyMatch(
                    question -> question == null || !Boolean.TRUE.equals(question.avoidLeading())
                        || blank(question.question()) || blank(question.purpose()))) {
                throw failure("PERSONA_AI_RESPONSE_INVALID", false, null);
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
            retryable
                ? "AI 서비스가 일시적으로 응답하지 않아 다시 시도합니다."
                : "Persona AI 결과를 안전하게 처리할 수 없습니다.",
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
