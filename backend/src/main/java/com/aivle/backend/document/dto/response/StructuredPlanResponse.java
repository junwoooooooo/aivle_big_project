package com.aivle.backend.document.dto.response;

import com.aivle.backend.common.entity.StructuredPlanStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StructuredPlanResponse(
    Long planId,
    Long projectId,
    Long sourceDocumentVersionId,
    int versionNumber,
    StructuredPlanStatus status,
    int completionRate,
    long version,
    LocalDateTime confirmedAt,
    Long confirmedBy,
    List<StructuredPlanSectionResponse> sections,
    List<StructuredMissingFieldResponse> missingFields,
    LocalDateTime createdAt,
    String parserVersion,
    String promptVersion,
    String modelName,
    String provider
) {
    public StructuredPlanResponse {
        sections = List.copyOf(sections);
        missingFields = List.copyOf(missingFields);
    }
}
