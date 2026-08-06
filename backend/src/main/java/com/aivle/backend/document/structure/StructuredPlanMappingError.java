package com.aivle.backend.document.structure;

public record StructuredPlanMappingError(
        StructuredPlanMappingErrorCode code,
        String sectionCode,
        String message
) {
}
