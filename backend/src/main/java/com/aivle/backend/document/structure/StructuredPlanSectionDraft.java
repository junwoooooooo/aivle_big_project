package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.PlanSectionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record StructuredPlanSectionDraft(
        BusinessPlanSectionCode sectionCode,
        Set<PlanSectionType> mappedPlanSectionTypes,
        String title,
        String extractedContent,
        StructuredItemStatus status,
        String reason,
        BigDecimal confidence,
        List<String> evidence,
        List<Integer> sourceBlockReferences
) {
    public StructuredPlanSectionDraft {
        mappedPlanSectionTypes = mappedPlanSectionTypes == null
                ? Set.of()
                : Set.copyOf(mappedPlanSectionTypes);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        sourceBlockReferences = sourceBlockReferences == null
                ? List.of()
                : List.copyOf(sourceBlockReferences);
    }
}
