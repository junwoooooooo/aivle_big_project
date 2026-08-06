package com.aivle.backend.integration.ai.openai;

import com.aivle.backend.config.AiProperties;
import com.aivle.backend.document.structure.*;
import com.aivle.backend.integration.ai.AiServiceClient;
import com.aivle.backend.integration.ai.document.*;
import com.aivle.backend.integration.ai.dto.AiJobAcceptedResponse;
import com.aivle.backend.integration.ai.dto.AiJobRequest;
import com.aivle.backend.integration.ai.dto.AiJobStatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class OpenAiDocumentStructureAdapter implements AiServiceClient {
    private final AiProperties properties;
    private final BusinessPlanSectionCatalog catalog;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiDocumentStructureAdapter(
        AiProperties properties,
        BusinessPlanSectionCatalog catalog,
        ObjectMapper objectMapper
    ) {
        this(properties, catalog, objectMapper, createRestClient(properties));
    }

    OpenAiDocumentStructureAdapter(
        AiProperties properties,
        BusinessPlanSectionCatalog catalog,
        ObjectMapper objectMapper,
        RestClient restClient
    ) {
        this.properties = properties;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient createRestClient(AiProperties properties) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public AiJobAcceptedResponse startJob(AiJobRequest request) {
        throw unsupportedLegacyOperation();
    }

    @Override
    public AiJobStatusResponse getStatus(String externalRequestId) {
        throw unsupportedLegacyOperation();
    }

    @Override
    public void cancel(String externalRequestId) {
        throw unsupportedLegacyOperation();
    }

    @Override
    public DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request) {
        validateConfiguration();
        try {
            String userContent = serializeInput(request);
            OpenAiTransportDtos.ChatRequest transportRequest =
                new OpenAiTransportDtos.ChatRequest(
                    properties.model(),
                    new OpenAiTransportDtos.ResponseFormat("json_object"),
                    List.of(
                        new OpenAiTransportDtos.Message("system", request.promptText()),
                        new OpenAiTransportDtos.Message("user", userContent)
                    )
                );
            String responseBody = restClient.post()
                .uri(properties.baseUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(transportRequest)
                .retrieve()
                .body(String.class);
            return parseResponse(responseBody, request);
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(
                "AI_NETWORK_TIMEOUT",
                "AI 서비스 응답이 지연되어 다시 시도합니다.",
                true,
                null,
                exception
            );
        } catch (AiClientException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    private String serializeInput(DocumentStructureAiRequest request) throws JacksonException {
        List<OpenAiTransportDtos.InputBlock> blocks = request.blocks().stream()
            .map(block -> new OpenAiTransportDtos.InputBlock(
                block.sequence(),
                block.blockType(),
                block.text(),
                block.sourceLocation(),
                block.headingLevel(),
                block.tableRow(),
                block.tableColumn()
            ))
            .toList();
        return objectMapper.writeValueAsString(new OpenAiTransportDtos.DocumentInput(blocks));
    }

    private DocumentStructureAiResponse parseResponse(
        String responseBody,
        DocumentStructureAiRequest request
    ) throws JacksonException {
        if (responseBody == null
            || responseBody.isBlank()
            || responseBody.getBytes(StandardCharsets.UTF_8).length > properties.maxResponseBytes()) {
            throw invalidResponse(null);
        }
        OpenAiTransportDtos.ChatResponse response =
            objectMapper.readValue(responseBody, OpenAiTransportDtos.ChatResponse.class);
        if (response.choices() == null
            || response.choices().isEmpty()
            || response.choices().get(0) == null
            || response.choices().get(0).message() == null
            || response.choices().get(0).message().content() == null) {
            throw invalidResponse(null);
        }
        OpenAiTransportDtos.StructuredResponse structured = objectMapper.readValue(
            response.choices().get(0).message().content(),
            OpenAiTransportDtos.StructuredResponse.class
        );
        List<AiStructuredPlanItem> items = validateAndConvert(structured);
        String rawHash = canonicalHash(items);
        AiStructuredPlanResult result = new AiStructuredPlanResult(
            "openai",
            properties.model(),
            request.promptVersion(),
            request.parserVersion(),
            items,
            rawHash,
            List.of()
        );
        return new DocumentStructureAiResponse(result, response.id());
    }

    private List<AiStructuredPlanItem> validateAndConvert(
        OpenAiTransportDtos.StructuredResponse response
    ) {
        if (response == null || response.items() == null
            || response.items().size() != catalog.all().size()) {
            throw invalidResponse(null);
        }
        EnumSet<BusinessPlanSectionCode> seen = EnumSet.noneOf(BusinessPlanSectionCode.class);
        List<AiStructuredPlanItem> items = new ArrayList<>();
        for (OpenAiTransportDtos.StructuredItem item : response.items()) {
            if (item == null || item.sectionCode() == null || item.status() == null) {
                throw invalidResponse(null);
            }
            BusinessPlanSectionCode code;
            StructuredItemStatus status;
            try {
                code = BusinessPlanSectionCode.valueOf(item.sectionCode().trim());
                status = StructuredItemStatus.valueOf(item.status().trim());
            } catch (IllegalArgumentException exception) {
                throw invalidResponse(exception);
            }
            if (!seen.add(code) || status == StructuredItemStatus.UNKNOWN) {
                throw invalidResponse(null);
            }
            items.add(new AiStructuredPlanItem(
                code.name(),
                item.sectionName(),
                status,
                item.extractedContent(),
                item.reason(),
                item.confidence(),
                item.evidence(),
                item.sourceBlockReferences()
            ));
        }
        if (seen.size() != BusinessPlanSectionCode.values().length) {
            throw invalidResponse(null);
        }
        return items.stream()
            .sorted(Comparator.comparingInt(item ->
                catalog.require(BusinessPlanSectionCode.valueOf(item.sectionCode())).sequence()))
            .toList();
    }

    private String canonicalHash(List<AiStructuredPlanItem> items) {
        List<OpenAiTransportDtos.CanonicalItem> canonicalItems = items.stream()
            .map(item -> new OpenAiTransportDtos.CanonicalItem(
                item.sectionCode(),
                item.sectionName(),
                item.status().name(),
                item.extractedContent(),
                item.reason(),
                item.confidence(),
                item.evidence(),
                item.sourceBlockReferences()
            ))
            .toList();
        try {
            String json = objectMapper.writeValueAsString(
                new OpenAiTransportDtos.CanonicalResult(canonicalItems)
            );
            return sha256(json);
        } catch (JacksonException exception) {
            throw invalidResponse(exception);
        }
    }

    private void validateConfiguration() {
        if (properties.baseUrl() == null
            || properties.baseUrl().isBlank()
            || properties.model() == null
            || properties.model().isBlank()
            || properties.apiKey() == null
            || properties.apiKey().isBlank()) {
            throw new AiClientException(
                "AI_CONFIGURATION_INVALID",
                "AI 서비스 설정이 완료되지 않았습니다.",
                false,
                null,
                null
            );
        }
    }

    private AiClientException httpFailure(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        boolean retryable = status.value() == 408
            || status.value() == 429
            || status.is5xxServerError();
        return new AiClientException(
            "AI_HTTP_" + status.value(),
            retryable
                ? "AI 서비스가 일시적으로 응답하지 않아 다시 시도합니다."
                : "AI 서비스 요청을 처리할 수 없습니다.",
            retryable,
            retryable ? parseRetryAfter(exception.getResponseHeaders()) : null,
            exception
        );
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(
                    value,
                    DateTimeFormatter.RFC_1123_DATE_TIME
                ).toInstant();
                Duration duration = Duration.between(Instant.now(), retryAt);
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (RuntimeException invalidDate) {
                return null;
            }
        }
    }

    private AiClientException invalidResponse(Throwable cause) {
        return new AiClientException(
            "AI_RESPONSE_INVALID",
            "AI 구조화 결과 형식이 올바르지 않습니다.",
            false,
            null,
            cause
        );
    }

    private UnsupportedOperationException unsupportedLegacyOperation() {
        return new UnsupportedOperationException(
            "legacy external AI job operations are not supported by this adapter"
        );
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
