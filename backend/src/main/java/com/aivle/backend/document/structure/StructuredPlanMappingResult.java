package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.StructuredPlanStatus;

import java.util.List;

public record StructuredPlanMappingResult(
        List<StructuredPlanSectionDraft> sectionDrafts,
        List<MissingFieldDraft> missingFieldDrafts,
        int completionRate,
        StructuredPlanStatus structuredPlanStatus,
        List<String> warnings,
        List<StructuredPlanMappingError> mappingErrors
) {
    public StructuredPlanMappingResult {
        sectionDrafts = List.copyOf(sectionDrafts);
        missingFieldDrafts = List.copyOf(missingFieldDrafts);
        warnings = List.copyOf(warnings);
        mappingErrors = List.copyOf(mappingErrors);
        if (completionRate < 0 || completionRate > 100) {
            throw new IllegalArgumentException("completionRate must be between 0 and 100");
        }
        if (structuredPlanStatus == StructuredPlanStatus.CONFIRMED) {
            throw new IllegalArgumentException("mapper must not confirm a structured plan");
        }
    }
}
