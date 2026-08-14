package com.aivle.backend.launchreadiness.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport.ModuleType;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
@Slf4j
public class ProfessionalAnalysisAiClient {
    private final RestClient client;
    private final AiServerProperties properties;

    public ProfessionalAnalysisAiClient(@Qualifier("aiServerLongRestClient") RestClient client,
            AiServerProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public JsonNode analyze(ModuleType moduleType, Map<String, String> input) {
        if (!properties.hasInternalApiKey()) {
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE,
                "AI 서버 내부 인증키가 설정되지 않았습니다.");
        }
        try {
            return client.post().uri("/internal/v1/launch-readiness/analyze")
                .headers(headers -> headers.set("X-Internal-Api-Key", properties.internalApiKey()))
                .body(Map.of("moduleType", moduleType.name(), "input", input))
                .retrieve().body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientResponseException exception) {
            log.error("Professional analysis AI rejected request: module={}, status={}, body={}",
                moduleType, exception.getStatusCode(), abbreviate(exception.getResponseBodyAsString(), 2_000));
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE,
                "전문 분석 AI를 호출할 수 없습니다. 잠시 후 다시 실행해 주세요.");
        } catch (RestClientException exception) {
            log.error("Professional analysis AI connection failure: module={}", moduleType, exception);
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE,
                "전문 분석 AI 서버에 연결할 수 없습니다. 잠시 후 다시 실행해 주세요.");
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
