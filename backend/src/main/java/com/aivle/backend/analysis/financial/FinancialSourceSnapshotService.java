package com.aivle.backend.analysis.financial;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import com.aivle.backend.project.entity.Project;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Captures only the stable, marketing/report-safe source facts used by a financial analysis. */
@Service
@RequiredArgsConstructor
public class FinancialSourceSnapshotService {
    private final ObjectMapper objectMapper;

    public String capture(Project project, FeasibilityAssessment assessment, Object userInput) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("project", Map.of("id", project.getId(), "title", project.getTitle(),
            "industryCategory", safe(project.getIndustryCategory())));
        root.put("structuredPlan", Map.of("id", assessment.getStructuredPlan().getId(),
            "version", assessment.getStructuredPlan().getVersionNumber(),
            "confirmed", assessment.getStructuredPlan().getConfirmedByUser()));
        root.put("sourceDocumentVersion", assessment.getSourceDocumentVersion().getId());
        root.put("feasibility", Map.of("id", assessment.getId(), "status", assessment.getStatus().name(),
            "verdict", assessment.getVerdict().name(), "summary", safe(assessment.getSummary()),
            "financialRisks", safe(assessment.getKeyRisksJson()),
            "validationTasks", safe(assessment.getValidationTasksSummaryJson())));
        root.put("userInput", userInput);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("financial source snapshot serialization failed", exception);
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
}
