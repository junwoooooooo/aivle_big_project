package com.aivle.backend.document.application.processing;

import java.util.List;

public record StructuredPlanProcessingSummary(
    List<SectionStatus> sections,
    List<String> warnings
) {
    public StructuredPlanProcessingSummary {
        sections = List.copyOf(sections);
        warnings = List.copyOf(warnings);
    }

    public record SectionStatus(String sectionCode, String status) {
    }
}
