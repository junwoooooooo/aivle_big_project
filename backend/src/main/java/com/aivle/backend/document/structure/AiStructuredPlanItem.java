package com.aivle.backend.document.structure;

import java.math.BigDecimal;
import java.util.List;

public record AiStructuredPlanItem(
        String sectionCode,
        String sectionName,
        StructuredItemStatus status,
        String extractedContent,
        String reason,
        BigDecimal confidence,
        List<String> evidence,
        List<Integer> sourceBlockReferences
) {
    public AiStructuredPlanItem {
        if (sectionCode == null || sectionCode.isBlank() || status == null) {
            throw new IllegalArgumentException("sectionCode and status are required");
        }
        if (confidence != null
                && (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        sourceBlockReferences = sourceBlockReferences == null
                ? List.of()
                : List.copyOf(sourceBlockReferences);
    }
}
