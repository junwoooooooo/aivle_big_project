package com.aivle.backend.integration.ai.persona;

import java.math.BigDecimal;
import java.util.List;

public record PersonaRecommendationAiRequest(
    Long projectId,
    Long structuredPlanId,
    Long feasibilityAssessmentId,
    Long sourceDocumentVersionId,
    String promptVersion,
    String catalogVersion,
    String promptText,
    List<Section> sections,
    FeasibilityContext feasibility,
    List<BaselinePersona> personas
) {
    public record Section(
        String code, String content, String itemStatus, String completionSource
    ) {}
    public record FeasibilityContext(
        String verdict, Integer overallScore, String confidence,
        List<Dimension> dimensions, List<ValidationTask> validationTasks,
        String legalRiskLevel
    ) {}
    public record Dimension(
        String code, Integer score, String confidence, String status,
        String finding, String evidenceJson
    ) {}
    public record ValidationTask(
        Long id, String code, String dimensionCode, String title, String reason,
        String priority, String validationMethod, String expectedEvidence
    ) {}
    public record BaselinePersona(
        String personaCode, String clusterId, String displayName, String ageGroup,
        String gender, Integer sampleSize, BigDecimal weightedShare,
        String dataSource, String dataVersion, String catalogVersion,
        String keyTraitsJson, String demographicSummaryJson,
        String evidenceMetricsJson, String limitationsJson
    ) {}
}
