package com.aivle.backend.document.dto.response;

import com.aivle.backend.common.entity.MissingFieldStatus;
import com.aivle.backend.common.entity.Priority;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;

public record StructuredMissingFieldResponse(
    Long fieldId,
    String fieldCode,
    BusinessPlanSectionCode sectionCode,
    String label,
    boolean required,
    MissingFieldStatus status,
    String reason,
    Priority priority,
    String userValue,
    long version
) {
}
