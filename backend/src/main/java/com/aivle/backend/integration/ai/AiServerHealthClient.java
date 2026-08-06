package com.aivle.backend.integration.ai;

import com.aivle.backend.integration.ai.dto.AiServerHealthResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiServerHealthClient {

    private final RestClient restClient;
    private final AiServerClientSupport support;

    public AiServerHealthClient(
        @Qualifier("aiServerRestClient")
        RestClient restClient,
        AiServerClientSupport support
    ) {
        this.restClient = restClient;
        this.support = support;
    }

    public AiServerHealthResponse checkHealth() {
        return checkHealth(null);
    }

    public AiServerHealthResponse checkHealth(String requestId) {
        return getHealth("/health", requestId);
    }

    public AiServerHealthResponse checkReady(String requestId) {
        return getHealth("/health/ready", requestId);
    }

    private AiServerHealthResponse getHealth(
        String path,
        String candidateRequestId
    ) {
        String requestId = support.resolveRequestId(
            candidateRequestId
        );
        return support.execute(
            requestId,
            () -> restClient.get()
                .uri(path)
                .headers(headers ->
                    support.addHeaders(headers, requestId)
                )
                .retrieve()
                .body(AiServerHealthResponse.class)
        );
    }
}
