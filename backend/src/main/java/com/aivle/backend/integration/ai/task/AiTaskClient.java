package com.aivle.backend.integration.ai.task;

import com.aivle.backend.integration.ai.AiServerClientSupport;
import com.aivle.backend.integration.ai.task.dto.AiTaskRequest;
import com.aivle.backend.integration.ai.task.dto.AiTaskResponse;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiTaskClient implements AiTaskGateway {

    private final RestClient restClient;
    private final AiServerClientSupport support;

    public AiTaskClient(
        @Qualifier("aiServerRestClient")
        RestClient restClient,
        AiServerClientSupport support
    ) {
        this.restClient = restClient;
        this.support = support;
    }

    @Override
    public AiTaskResponse execute(AiTaskRequest request) {
        String requestId = support.resolveRequestId(
            request.requestId()
        );
        AiTaskRequest outbound = new AiTaskRequest(
            requestId,
            request.taskId(),
            request.taskType(),
            request.schemaVersion(),
            request.input(),
            request.context(),
            request.options(),
            request.artifacts(),
            request.outputTargets()
        );
        return support.execute(
            requestId,
            () -> {
                AiTaskResponse response = restClient.post()
                    .uri("/internal/v1/tasks")
                    .headers(headers ->
                        support.addHeaders(headers, requestId)
                    )
                    .body(outbound)
                    .retrieve()
                    .body(AiTaskResponse.class);
                validate(outbound, response);
                return response;
            }
        );
    }

    private void validate(
        AiTaskRequest request,
        AiTaskResponse response
    ) {
        if (
            response == null
            || !Objects.equals(
                request.requestId(),
                response.requestId()
            )
            || !Objects.equals(
                request.taskId(),
                response.taskId()
            )
            || request.taskType() != response.taskType()
            || !Objects.equals(
                request.schemaVersion(),
                response.schemaVersion()
            )
            || !"SUCCEEDED".equals(response.status())
            || response.result() == null
            || response.execution() == null
            || (
                request.taskType()
                == AiTaskType.SYSTEM_ARTIFACT_SMOKE_TEST
                && (
                    response.artifacts() == null
                    || response.artifacts().isEmpty()
                )
            )
        ) {
            throw new RestClientException(
                "AI task response contract mismatch"
            );
        }
    }
}
