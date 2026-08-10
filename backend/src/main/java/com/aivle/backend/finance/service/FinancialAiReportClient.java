package com.aivle.backend.finance.service;

import com.aivle.backend.integration.ai.AiServerProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls the AI server only for narrative interpretation; calculation values remain backend-owned. */
@Component
public class FinancialAiReportClient {
    private final RestClient client;
    private final AiServerProperties properties;

    public FinancialAiReportClient(
        @Qualifier("aiServerRestClient") RestClient client,
        AiServerProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    public AiReport generate(Map<String, Object> input) {
        if (!properties.hasInternalApiKey()) {
            throw new IllegalStateException("AI server internal API key is missing");
        }
        try {
            return client.post()
                .uri("/internal/v1/financial/report")
                .headers(headers -> headers.set("X-Internal-Api-Key", properties.internalApiKey()))
                .body(Map.of("input", input))
                .retrieve()
                .body(AiReport.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("financial AI report request failed", exception);
        }
    }

    public record AiReport(String headline, List<String> findings, List<String> cautions, List<String> recommendedActions, String disclaimer) { }
}
