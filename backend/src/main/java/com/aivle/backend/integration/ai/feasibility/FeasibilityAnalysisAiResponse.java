package com.aivle.backend.integration.ai.feasibility;

import static com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.AnalysisType;
import java.util.List;

public record FeasibilityAnalysisAiResponse(
    String provider,
    String model,
    String providerRequestId,
    String summary,
    List<String> keyStrengths,
    List<String> keyRisks,
    List<Dimension> dimensions,
    List<Group> groups,
    List<ValidationTask> validationTasks
) {
    public FeasibilityAnalysisAiResponse {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /** 묶음 서술 없이 응답하는 하위호환 경로(구 어댑터·테스트). 점수·판정은 백엔드가 계산한다. */
    public FeasibilityAnalysisAiResponse(
        String provider, String model, String providerRequestId, String summary,
        List<String> keyStrengths, List<String> keyRisks,
        List<Dimension> dimensions, List<ValidationTask> validationTasks
    ) {
        this(provider, model, providerRequestId, summary, keyStrengths, keyRisks,
            dimensions, List.of(), validationTasks);
    }

    /**
     * 묶음(시장·BM·기술·운영) 단위 서술. 점수와 판정은 백엔드가 결정론으로 계산하므로
     * 여기에는 담지 않는다 — AI는 "무엇을 어떻게 읽었는가"만 쓴다.
     */
    public record Group(
        AnalysisType analysisType,
        String headline,
        String summary,
        List<String> keyStrengths,
        List<String> keyRisks,
        String nextFocus
    ) {
        public Group {
            keyStrengths = keyStrengths == null ? List.of() : List.copyOf(keyStrengths);
            keyRisks = keyRisks == null ? List.of() : List.copyOf(keyRisks);
        }
    }
    public record Dimension(
        DimensionCode code,
        Integer score,
        Confidence confidence,
        DimensionStatus status,
        String finding,
        String rationale,
        List<String> strengths,
        List<String> risks,
        List<String> assumptions,
        List<Evidence> evidence,
        List<String> sourceSectionCodes,
        List<Long> legalFindingIds,
        List<String> recommendedActions
    ) {}

    public record Evidence(
        EvidenceType type, String description, String reference
    ) {}

    public record ValidationTask(
        String code,
        DimensionCode dimensionCode,
        String title,
        String description,
        String reason,
        ValidationPriority priority,
        String validationMethod,
        String expectedEvidence
    ) {}
}
