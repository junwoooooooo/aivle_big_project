package com.aivle.backend.integration.ai;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.document.structure.AiStructuredPlanItem;
import com.aivle.backend.document.structure.AiStructuredPlanResult;
import com.aivle.backend.document.structure.StructuredItemStatus;
import com.aivle.backend.integration.ai.document.DocumentStructureAiRequest;
import com.aivle.backend.integration.ai.document.DocumentStructureAiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class MockAiServiceClient implements AiServiceClient {
    @Override
    public AiJobAcceptedResponse startJob(AiJobRequest request) {
        return new AiJobAcceptedResponse("mock-" + request.jobId(), JobStatus.QUEUED);
    }

    @Override
    public AiJobStatusResponse getStatus(String externalRequestId) {
        return new AiJobStatusResponse(externalRequestId, JobStatus.QUEUED, 0,
                "mock queued", null, null, null);
    }

    @Override
    public void cancel(String externalRequestId) {
        // Mock has no external state; cancellation is intentionally idempotent.
    }

    @Override
    public DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request) {
        Integer firstSequence = request.blocks().isEmpty()
            ? null
            : request.blocks().get(0).sequence();
        List<AiStructuredPlanItem> items = request.sections().stream()
            .map(section -> new AiStructuredPlanItem(
                section.code(),
                section.displayName(),
                StructuredItemStatus.PRESENT,
                "Mock structured content for " + section.displayName(),
                "",
                null,
                List.of("Mock adapter result"),
                firstSequence == null ? List.of() : List.of(firstSequence)
            ))
            .toList();
        return new DocumentStructureAiResponse(
            new AiStructuredPlanResult(
                "mock",
                "mock-document-structure-v1",
                request.promptVersion(),
                request.parserVersion(),
                items,
                null,
                List.of("MOCK_AI_RESULT")
            ),
            "mock-structure-" + request.jobId()
        );
    }
}
