package com.aivle.backend.integration.ai.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.*;
import com.aivle.backend.common.entity.AnalysisType;
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
            dimensions, groups(), List.copyOf(tasks));
    }

    /**
     * 묶음 서술 3건. 점수·판정은 백엔드가 계산하므로 여기서는 "무엇을 읽었는가"만 쓴다.
     * 묶음마다 문구가 달라야 화면 검증이 의미를 갖는다.
     */
    private List<FeasibilityAnalysisAiResponse.Group> groups() {
        return List.of(
            new FeasibilityAnalysisAiResponse.Group(
                AnalysisType.MARKET,
                "문제와 고객은 계획서에서 읽히지만, 시장 규모 주장은 외부 출처로 확인해야 합니다.",
                "해결하려는 문제·목표 고객·시장 매력도·경쟁 포지션을 계획서 기준으로 함께 봤습니다.",
                List.of("문제와 목표 고객이 계획서 문장으로 특정되어 있습니다."),
                List.of("시장 규모와 경쟁 주장은 계획서 안에서만 확인되어 외부 검증이 필요합니다."),
                "시장 규모 주장의 출처와 산출 기준부터 확보하세요."),
            new FeasibilityAnalysisAiResponse.Group(
                AnalysisType.BUSINESS_MODEL,
                "제품과 수익 구조는 연결되지만, 재무 가정이 아직 검증 가능한 형태가 아닙니다.",
                "제품·해결책 적합성, 수익 구조, 시장 진입 전략, 재무 실행 가능성을 하나로 봤습니다. "
                    + "여기서 보는 재무는 '가정이 검증 가능한가'이며 숫자 계산은 다음 재무 분석의 몫입니다.",
                List.of("제품이 앞서 정의한 문제와 고객에 연결되어 있습니다."),
                List.of("가격·원가·판매량 가정의 근거와 단위가 계획서에 명시되지 않았습니다."),
                "가격·원가·판매량 가정을 근거와 함께 표로 정리하세요."),
            new FeasibilityAnalysisAiResponse.Group(
                AnalysisType.TECHNOLOGY_OPERATION,
                "실행 계획은 있으나 규제 미확정 사항이 일정과 비용을 흔들 수 있습니다.",
                "기술·생산·운영 실행 역량과, 앞선 규제 검토 결과를 사업 실행 제약으로 번역해 함께 봤습니다.",
                List.of("생산·운영 방식이 계획서에 서술되어 있습니다."),
                List.of("규제 검토에서 남은 미확정 항목이 실행 일정에 반영되지 않았습니다."),
                "규제 검토의 열린 항목을 담당자와 완료 예정일까지 정하세요."));
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
