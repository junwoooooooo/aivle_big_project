package com.aivle.backend.document.dto.response;

import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.document.structure.StructuredItemStatus;

import java.math.BigDecimal;
import java.util.List;

public record StructuredPlanSectionResponse(
    BusinessPlanSectionCode sectionCode,
    String displayName,
    int sequence,
    StructuredItemStatus status,
    String extractedContent,
    String reason,
    BigDecimal confidence,
    List<String> evidence,
    List<Integer> sourceBlockReferences
) {
    public StructuredPlanSectionResponse {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        sourceBlockReferences = sourceBlockReferences == null
            ? List.of()
            : List.copyOf(sourceBlockReferences);
    }
}
