package com.aivle.backend.integration.ai.financial;

import com.aivle.backend.analysis.financial.entity.FinancialTypes.AssumptionSourceType;
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
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 재무 가정 추출 어댑터.
 *
 * <p><b>인용 검증 실패는 분석 전체를 죽이지 않는다.</b> 지어낸 인용을 문 가정 하나 때문에
 * 나머지 가정과 서술까지 버리면 사용자가 잃는 게 더 크다. 해당 가정만 떨어뜨리고
 * (기본값이 있으면 DEFAULT로 강등) 나머지는 그대로 살린다 — 우아한 강등.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class OpenAiFinancialAdapter implements FinancialAiClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiFinancialAdapter(AiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createClient(properties));
    }

    OpenAiFinancialAdapter(
        AiProperties properties, ObjectMapper objectMapper, RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public FinancialAiResponse extract(FinancialAiRequest request) {
        validateConfiguration();
        try {
            String input = objectMapper.writeValueAsString(request);
            if (input.length() > properties.maxInputCharacters()) {
                throw failure("FINANCIAL_AI_INPUT_TOO_LARGE", false, null);
            }
            String body = restClient.post()
                .uri(properties.baseUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ChatRequest(properties.model(), new ResponseFormat("json_object"),
                    List.of(new Message("system", request.promptText()),
                        new Message("user", input))))
                .retrieve().body(String.class);
            return parse(body, request);
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            boolean retryable = status.value() == 408 || status.value() == 429
                || status.is5xxServerError();
            throw failure("FINANCIAL_AI_HTTP_" + status.value(), retryable, exception);
        } catch (ResourceAccessException exception) {
            throw failure("FINANCIAL_AI_NETWORK_TIMEOUT", true, exception);
        } catch (AiClientException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw failure("FINANCIAL_AI_RESPONSE_INVALID", false, exception);
        }
    }

    private FinancialAiResponse parse(String body, FinancialAiRequest request)
        throws JacksonException {
        if (body == null || body.isBlank()
            || body.getBytes(StandardCharsets.UTF_8).length > properties.maxResponseBytes()) {
            throw failure("FINANCIAL_AI_RESPONSE_INVALID", false, null);
        }
        ChatResponse transport = objectMapper.readValue(body, ChatResponse.class);
        if (transport.choices() == null || transport.choices().isEmpty()
            || transport.choices().get(0) == null
            || transport.choices().get(0).message() == null
            || transport.choices().get(0).message().content() == null) {
            throw failure("FINANCIAL_AI_RESPONSE_INVALID", false, null);
        }
        FinancialAiResponse raw = objectMapper.readValue(
            transport.choices().get(0).message().content(), FinancialAiResponse.class);
        return new FinancialAiResponse(
            "openai", properties.model(), transport.id(),
            keepVerifiable(raw.assumptions(), sourceText(request)),
            raw.conflicts(), raw.narrative());
    }

    /**
     * 인용이 기획서 원문에 실제로 있는 가정만 남긴다.
     * 인용을 못 대는 PLAN 가정은 지어낸 것으로 보고 떨어뜨리며, DEFAULT/USER는 그대로 통과시킨다.
     */
    private List<FinancialAiResponse.Assumption> keepVerifiable(
        List<FinancialAiResponse.Assumption> assumptions, String sourceText
    ) {
        List<FinancialAiResponse.Assumption> kept = new ArrayList<>();
        for (var item : assumptions) {
            if (item == null || item.key() == null || item.key().isBlank()
                || item.source() == null || item.source().type() == null) {
                continue;
            }
            if (item.source().type() == AssumptionSourceType.PLAN && !quoted(item, sourceText)) {
                continue;
            }
            kept.add(new FinancialAiResponse.Assumption(
                item.key(), item.label(), item.value(), item.unit(), item.source(),
                item.candidates().stream()
                    .filter(candidate -> candidate != null && candidate.quote() != null
                        && sourceText.contains(candidate.quote()))
                    .toList()));
        }
        return List.copyOf(kept);
    }

    private boolean quoted(FinancialAiResponse.Assumption item, String sourceText) {
        String quote = item.source().quote();
        return quote != null && !quote.isBlank() && sourceText.contains(quote);
    }

    private String sourceText(FinancialAiRequest request) {
        StringBuilder builder = new StringBuilder();
        for (var section : request.sections()) {
            if (section.content() != null) {
                builder.append(section.content()).append('\n');
            }
        }
        return builder.toString();
    }

    private void validateConfiguration() {
        if (blank(properties.baseUrl()) || blank(properties.model()) || blank(properties.apiKey())) {
            throw failure("AI_CONFIGURATION_INVALID", false, null);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private AiClientException failure(String code, boolean retryable, Throwable cause) {
        return new AiClientException(code,
            retryable ? "AI 서비스가 일시적으로 응답하지 않아 다시 시도합니다."
                : "재무 분석 AI 결과를 안전하게 처리할 수 없습니다.",
            retryable, null, cause);
    }

    private static RestClient createClient(AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
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
