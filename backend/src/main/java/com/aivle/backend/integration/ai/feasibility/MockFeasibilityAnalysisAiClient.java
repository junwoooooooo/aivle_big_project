package com.aivle.backend.integration.ai.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.RiskLevel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockFeasibilityAnalysisAiClient implements FeasibilityAnalysisAiClient {
    @Override
    public FeasibilityAnalysisAiResponse analyze(FeasibilityAnalysisAiRequest request) {
        List<FeasibilityAnalysisAiResponse.Dimension> dimensions = request.catalog().stream()
            .map(item -> dimension(request, item))
            .toList();
        List<FeasibilityAnalysisAiResponse.ValidationTask> tasks = new ArrayList<>();
        tasks.add(task("VERIFY_MARKET_SOURCES", DimensionCode.MARKET_ATTRACTIVENESS,
            "시장 주장 외부 검증", "계획에 적힌 시장 주장과 최신 외부 출처를 대조하세요.",
            "Mock 분석은 시장 수치나 통계를 생성하지 않습니다.", ValidationPriority.HIGH,
            "공공 통계·산업 보고서의 발행일과 산출 기준을 확인",
            "출처 URL, 발행일, 산출 범위와 원문 인용"));
        tasks.add(task("VALIDATE_FINANCIAL_ASSUMPTIONS", DimensionCode.FINANCIAL_VIABILITY,
            "재무 가정 검증", "매출·가격·원가·고정비 가정을 실제 근거와 함께 입력하세요.",
            "구조화 계획만으로 신뢰할 계산 입력을 확정할 수 없습니다.", ValidationPriority.HIGH,
            "가정별 소유자·근거·기간·단위를 명시하고 민감도 검토",
            "검증된 가격, 판매량, 변동비, 고정비 가정표"));
        if (request.legalReview().overallRiskLevel() == RiskLevel.HIGH
            || request.legalReview().overallRiskLevel() == RiskLevel.CRITICAL
            || !request.legalReview().questions().isEmpty()) {
            tasks.add(task("RESOLVE_LEGAL_CONSTRAINTS", DimensionCode.LEGAL_AND_REGULATORY,
                "법률·규제 미확정 사항 확인", "법률 사전검토의 고위험 항목과 열린 질문을 확인하세요.",
                "미확정 규제 조건은 실행 일정과 비용에 영향을 줄 수 있습니다.",
                ValidationPriority.HIGH, "관할 기관 또는 자격 있는 전문가에게 적용 여부 확인",
                "적용 법령, 인허가 요건, 책임자와 완료 예정일"));
        }
        return new FeasibilityAnalysisAiResponse(
            "mock", "mock-feasibility-analysis-v1",
            "mock-feasibility-" + request.structuredPlanId() + "-" + request.legalReviewId(),
            "확정된 계획과 법률 사전검토를 기준으로 강점, 위험, 추가 검증 과제를 정리했습니다.",
            List.of("확정된 구조화 계획과 출처 문서 버전이 입력으로 고정되어 있습니다."),
            List.of("시장·고객·재무 주장은 외부 검증 전 가정으로 취급해야 합니다."),
            dimensions, List.copyOf(tasks));
    }

    private FeasibilityAnalysisAiResponse.Dimension dimension(
        FeasibilityAnalysisAiRequest request,
        FeasibilityAnalysisAiRequest.CatalogDimension catalog
    ) {
        List<FeasibilityAnalysisAiRequest.Section> sources = request.sections().stream()
            .filter(section -> catalog.sourceSectionCodes().contains(section.code()))
            .toList();
        boolean documented = sources.stream()
            .anyMatch(section -> section.content() != null && !section.content().isBlank());
        boolean completedByUser = request.completions().stream()
            .anyMatch(item -> catalog.sourceSectionCodes().contains(item.sectionCode())
                && "FILLED".equals(item.status())
                && item.userValue() != null && !item.userValue().isBlank());
        boolean waived = request.completions().stream()
            .anyMatch(item -> catalog.sourceSectionCodes().contains(item.sectionCode())
                && "WAIVED".equals(item.status()));
        boolean sufficient = documented || completedByUser;
        boolean externalBoundary = catalog.code() == DimensionCode.MARKET_ATTRACTIVENESS
            || catalog.code() == DimensionCode.FINANCIAL_VIABILITY
            || catalog.code() == DimensionCode.TARGET_CUSTOMER;
        Integer score = sufficient ? (externalBoundary ? 62 : 70) : null;
        DimensionStatus status = !sufficient
            ? DimensionStatus.INSUFFICIENT_INFORMATION
            : externalBoundary || waived
                ? DimensionStatus.NEEDS_VALIDATION : DimensionStatus.ASSESSED;
        Confidence confidence = sufficient && !externalBoundary ? Confidence.MEDIUM : Confidence.LOW;
        List<FeasibilityAnalysisAiResponse.Evidence> evidence = new ArrayList<>();
        for (var section : sources) {
            if (section.content() != null && !section.content().isBlank()) {
                evidence.add(new FeasibilityAnalysisAiResponse.Evidence(
                    EvidenceType.DOCUMENT_FACT, "확정 계획의 해당 섹션에 내용이 있습니다.", section.code()));
            }
        }
        request.completions().stream()
            .filter(item -> catalog.sourceSectionCodes().contains(item.sectionCode()))
            .forEach(item -> evidence.add(new FeasibilityAnalysisAiResponse.Evidence(
                "FILLED".equals(item.status()) ? EvidenceType.USER_ASSUMPTION
                    : EvidenceType.EXTERNAL_VERIFICATION_REQUIRED,
                "사용자 보완 상태: " + item.status(), item.fieldCode())));
        List<Long> legalIds = catalog.code() == DimensionCode.LEGAL_AND_REGULATORY
            ? request.legalReview().findings().stream().map(
                FeasibilityAnalysisAiRequest.LegalFinding::id).toList()
            : List.of();
        if (catalog.code() == DimensionCode.LEGAL_AND_REGULATORY) {
            evidence.add(new FeasibilityAnalysisAiResponse.Evidence(
                EvidenceType.LEGAL_REVIEW, "최신 법률 사전검토를 실행 제약으로 반영했습니다.",
                "legal-review:" + request.legalReviewId()));
        }
        return new FeasibilityAnalysisAiResponse.Dimension(
            catalog.code(), score, confidence, status,
            sufficient ? "확정 계획에 분석 가능한 관련 정보가 있습니다."
                : "현재 입력만으로 이 차원을 평가하기에 정보가 부족합니다.",
            sufficient ? "문서 또는 사용자 보완값의 존재를 확인했으며 사실성은 별도 검증 대상입니다."
                : "누락 정보를 0점으로 해석하지 않고 검증 과제로 남깁니다.",
            sufficient ? List.of("관련 계획 내용이 명시되어 있습니다.") : List.of(),
            externalBoundary
                ? List.of("외부 자료로 검증되지 않은 가정이 포함될 수 있습니다.")
                : waived ? List.of("일부 입력이 WAIVED 상태입니다.") : List.of(),
            completedByUser ? List.of("사용자 보완값은 검증 전 가정입니다.") : List.of(),
            List.copyOf(evidence), catalog.sourceSectionCodes(), legalIds,
            List.of("관련 근거의 출처, 작성일과 책임자를 확인하세요."));
    }

    private FeasibilityAnalysisAiResponse.ValidationTask task(
        String code, DimensionCode dimension, String title, String description, String reason,
        ValidationPriority priority, String method, String evidence
    ) {
        return new FeasibilityAnalysisAiResponse.ValidationTask(
            code, dimension, title, description, reason, priority, method, evidence);
    }
}
