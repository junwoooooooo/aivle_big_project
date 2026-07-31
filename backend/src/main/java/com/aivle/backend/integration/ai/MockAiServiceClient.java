package com.aivle.backend.integration.ai;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.document.structure.AiStructuredPlanItem;
import com.aivle.backend.document.structure.AiStructuredPlanResult;
import com.aivle.backend.document.structure.StructuredItemStatus;
import com.aivle.backend.integration.ai.document.DocumentStructureAiRequest;
import com.aivle.backend.integration.ai.document.DocumentStructureAiResponse;
import com.aivle.backend.integration.ai.document.DocumentStructureBlock;
import com.aivle.backend.integration.ai.document.DocumentStructureSection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * Mock은 문서를 실제로 분류하지 못하므로 섹션 배정은 임의다.
     * 다만 업로드된 원문 block을 그대로 실어 보낸다 — 자리표시자 문자열만 넘기면
     * 후속 분석(법률·타당성)이 분석할 내용 자체를 잃는다. 배정은 결정론적이고
     * 각 block은 정확히 한 섹션에만 들어간다.
     */
    @Override
    public DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request) {
        List<DocumentStructureBlock> blocks = request.blocks();
        List<DocumentStructureSection> sections = request.sections();
        List<AiStructuredPlanItem> items = new ArrayList<>();

        for (int index = 0; index < sections.size(); index++) {
            DocumentStructureSection section = sections.get(index);
            List<DocumentStructureBlock> slice = slice(blocks, index, sections.size());
            String extracted = slice.stream()
                .map(DocumentStructureBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
            List<Integer> references = slice.stream()
                .map(DocumentStructureBlock::sequence)
                .toList();

            items.add(new AiStructuredPlanItem(
                section.code(),
                section.displayName(),
                StructuredItemStatus.PRESENT,
                extracted.isBlank()
                    ? "Mock structured content for " + section.displayName()
                    : extracted,
                "",
                null,
                List.of("Mock adapter result"),
                references
            ));
        }
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

    /** block을 섹션 수만큼 연속 구간으로 균등 분할한다. 중복도 누락도 없다. */
    private static List<DocumentStructureBlock> slice(
        List<DocumentStructureBlock> blocks, int index, int parts
    ) {
        if (blocks.isEmpty() || parts <= 0) {
            return List.of();
        }
        int base = blocks.size() / parts;
        int remainder = blocks.size() % parts;
        int start = index * base + Math.min(index, remainder);
        int end = start + base + (index < remainder ? 1 : 0);
        return start >= end ? List.of() : blocks.subList(start, end);
    }
}
