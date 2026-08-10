package com.aivle.backend.analysis.financial.service;

import com.aivle.backend.integration.ai.AiServerClientSupport;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the AI server only for narrative interpretation; calculation values remain backend-owned. */
@Component
public class FinancialAiReportClient {
    private final RestClient client; private final AiServerClientSupport support;
    public FinancialAiReportClient(@Qualifier("aiServerRestClient") RestClient client, AiServerClientSupport support) { this.client = client; this.support = support; }
    public AiReport generate(Map<String, Object> input) {
        String requestId = support.resolveRequestId(null);
        return support.execute(requestId, () -> client.post().uri("/internal/v1/financial/report")
            .headers(h -> support.addHeaders(h, requestId)).body(Map.of("input", input)).retrieve().body(AiReport.class));
    }
    public record AiReport(String headline, List<String> findings, List<String> cautions, List<String> recommendedActions, String disclaimer) { }
}
