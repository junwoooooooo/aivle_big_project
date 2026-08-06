package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.MissingFieldStatus;
import com.aivle.backend.common.entity.Priority;

public record MissingFieldDraft(
        String fieldCode,
        String label,
        boolean required,
        MissingFieldStatus status,
        String reason,
        Priority priority,
        BusinessPlanSectionCode sectionCode
) {
}
