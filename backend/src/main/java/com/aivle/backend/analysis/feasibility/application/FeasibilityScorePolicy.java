package com.aivle.backend.analysis.feasibility.application;

import com.aivle.backend.analysis.feasibility.FeasibilityDimensionCatalog;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.AnalysisType;
import com.aivle.backend.common.entity.RiskLevel;
import com.aivle.backend.integration.ai.feasibility.FeasibilityAnalysisAiResponse;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FeasibilityScorePolicy {
    public Outcome evaluate(
        List<FeasibilityAnalysisAiResponse.Dimension> dimensions,
        List<FeasibilityAnalysisAiResponse.ValidationTask> tasks,
        RiskLevel legalRisk
    ) {
        if (dimensions.stream().anyMatch(item -> item.score() == null)) {
            return new Outcome(null, Confidence.LOW, Verdict.INSUFFICIENT_INFORMATION,
                AssessmentStatus.NEEDS_VALIDATION);
        }
        int weighted = dimensions.stream().mapToInt(item ->
            item.score() * FeasibilityDimensionCatalog.get(item.code()).weight()).sum();
        // 카탈로그 가중치 합(=100)으로 정규화한다. 상수로 나누면 카탈로그를 바꿨을 때
        // 예외 없이 점수만 조용히 왜곡되므로 실제 합을 쓴다.
        int score = Math.round(weighted / (float) totalWeight());
        boolean severeDimension = dimensions.stream().anyMatch(item -> item.score() < 40);
        boolean legalConstraint = legalRisk == RiskLevel.HIGH || legalRisk == RiskLevel.CRITICAL;
        Verdict verdict;
        if (severeDimension || score < 50) verdict = Verdict.HIGH_RISK;
        else if (score < 75 || legalConstraint || !tasks.isEmpty()) verdict = Verdict.CONDITIONAL;
        else verdict = Verdict.PROMISING;
        Confidence confidence = dimensions.stream().allMatch(
            item -> item.confidence() == Confidence.HIGH) ? Confidence.HIGH
            : dimensions.stream().anyMatch(item -> item.confidence() == Confidence.LOW)
                ? Confidence.LOW : Confidence.MEDIUM;
        AssessmentStatus status = tasks.isEmpty()
            ? AssessmentStatus.COMPLETED : AssessmentStatus.NEEDS_VALIDATION;
        return new Outcome(score, confidence, verdict, status);
    }

    /**
     * 묶음(시장·BM·기술·운영)별 점수와 판정. 총점과 같은 임계값을 쓰되
     * 분모만 묶음 가중치 합으로 바꾼다 — 묶음마다 합이 다르므로(44/44/12)
     * 정규화하지 않으면 묶음 간 점수를 비교할 수 없다.
     *
     * <p>묶음 안에 점수 미상 차원이 하나라도 있으면 묶음 점수는 null이다.
     * 총점 정책과 같은 규칙으로, 일부만 아는 상태를 아는 척하지 않기 위함이다.
     */
    public List<GroupOutcome> evaluateGroups(
        List<FeasibilityAnalysisAiResponse.Dimension> dimensions,
        List<FeasibilityAnalysisAiResponse.ValidationTask> tasks
    ) {
        List<GroupOutcome> outcomes = new ArrayList<>();
        int order = 1;
        for (AnalysisType group : AnalysisType.values()) {
            Set<DimensionCode> codes = FeasibilityDimensionCatalog.byGroup(group).stream()
                .map(FeasibilityDimensionCatalog.Definition::code)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DimensionCode.class)));
            List<FeasibilityAnalysisAiResponse.Dimension> members = dimensions.stream()
                .filter(item -> codes.contains(item.code())).toList();

            Integer score = null;
            if (!members.isEmpty() && members.stream().allMatch(item -> item.score() != null)) {
                int weighted = members.stream().mapToInt(item ->
                    item.score() * FeasibilityDimensionCatalog.get(item.code()).weight()).sum();
                score = Math.round(weighted / (float) FeasibilityDimensionCatalog.groupWeight(group));
            }
            boolean groupTask = tasks.stream()
                .anyMatch(task -> task.dimensionCode() != null && codes.contains(task.dimensionCode()));
            outcomes.add(new GroupOutcome(group, order++, score,
                groupVerdict(score, members, groupTask)));
        }
        return outcomes;
    }

    private Verdict groupVerdict(
        Integer score, List<FeasibilityAnalysisAiResponse.Dimension> members, boolean groupTask
    ) {
        if (score == null) {
            return Verdict.INSUFFICIENT_INFORMATION;
        }
        if (members.stream().anyMatch(item -> item.score() < 40) || score < 50) {
            return Verdict.HIGH_RISK;
        }
        return score < 75 || groupTask ? Verdict.CONDITIONAL : Verdict.PROMISING;
    }

    private int totalWeight() {
        return FeasibilityDimensionCatalog.all().stream()
            .mapToInt(FeasibilityDimensionCatalog.Definition::weight).sum();
    }

    public record Outcome(
        Integer overallScore, Confidence confidence, Verdict verdict, AssessmentStatus status
    ) {}

    public record GroupOutcome(
        AnalysisType group, int displayOrder, Integer score, Verdict verdict
    ) {}
}
