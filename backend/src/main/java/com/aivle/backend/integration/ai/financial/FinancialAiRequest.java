package com.aivle.backend.integration.ai.financial;

import com.aivle.backend.common.entity.AnalysisType;
import java.util.List;

/**
 * 재무 가정 추출 요청. 입력은 타당성 묶음 결과 + 기획서의 가격·원가·매출 섹션이다.
 *
 * <p>단가는 비즈니스 모델 섹션, 원가·마진은 원가·수익성 섹션, 수량·매출은 판매 목표 섹션에
 * 흩어져 있으므로 세 섹션을 함께 보낸다(표준 양식 6·7·8번).
 */
public record FinancialAiRequest(
    Long projectId,
    Long structuredPlanId,
    Long feasibilityAssessmentId,
    String promptVersion,
    String promptText,
    List<Section> sections,
    List<GroupSummary> feasibilityGroups
) {
    public FinancialAiRequest {
        sections = sections == null ? List.of() : List.copyOf(sections);
        feasibilityGroups = feasibilityGroups == null ? List.of() : List.copyOf(feasibilityGroups);
    }

    /** {@code content}는 인용 검증의 기준 원문이다 — AI가 여기 없는 문장을 인용하면 탈락한다. */
    public record Section(String code, String title, String content) {}

    /** 타당성 묶음 판정 — 재무 서술이 앞 단계와 어긋나지 않게 참고용으로만 싣는다. */
    public record GroupSummary(AnalysisType analysisType, Integer score, String headline) {}
}
