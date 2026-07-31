package com.aivle.backend.analysis.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.DimensionCode;
import com.aivle.backend.common.entity.AnalysisType;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import java.util.List;

public final class FeasibilityDimensionCatalog {
    /**
     * 이 값은 멱등키(FeasibilityCommandService)와 uk_feasibility_assessment_input(V8)에 들어간다.
     * 카탈로그를 바꾸면 반드시 올려야 기존 프로젝트가 새 구조로 재실행된다.
     * v2: 차원 10개를 시장/BM/기술·운영 3묶음으로 그룹핑 (차원 코드·가중치는 불변).
     */
    public static final String VERSION = "feasibility-catalog-v2";

    private static final List<Definition> DEFINITIONS = List.of(
        definition(DimensionCode.PROBLEM_AND_NEED, AnalysisType.MARKET, "문제와 필요", 1, 12,
            "해결하려는 문제와 필요가 구체적인지 확인합니다.",
            BusinessPlanSectionCode.BUSINESS_OVERVIEW),
        definition(DimensionCode.TARGET_CUSTOMER, AnalysisType.MARKET, "목표 고객", 2, 12,
            "고객 범위와 해결할 필요가 검증 가능한지 확인합니다.",
            BusinessPlanSectionCode.TARGET_CUSTOMER),
        definition(DimensionCode.MARKET_ATTRACTIVENESS, AnalysisType.MARKET, "시장 매력도", 3, 10,
            "시장 주장과 출처, 외부 검증 필요성을 확인합니다.",
            BusinessPlanSectionCode.MARKET_SIZE),
        definition(DimensionCode.COMPETITIVE_POSITION, AnalysisType.MARKET, "경쟁 포지션", 4, 10,
            "대안과 차별화 주장이 근거를 갖추었는지 확인합니다.",
            BusinessPlanSectionCode.COMPETITIVE_ANALYSIS),
        definition(DimensionCode.PRODUCT_SOLUTION_FIT, AnalysisType.BUSINESS_MODEL, "제품·해결책 적합성", 5, 10,
            "제안한 제품·서비스가 문제와 고객에 연결되는지 확인합니다.",
            BusinessPlanSectionCode.PRODUCT_SERVICE),
        definition(DimensionCode.BUSINESS_MODEL, AnalysisType.BUSINESS_MODEL, "비즈니스 모델", 6, 12,
            "수익 구조와 가치 전달 방식의 가정을 확인합니다.",
            BusinessPlanSectionCode.BUSINESS_MODEL),
        definition(DimensionCode.GO_TO_MARKET, AnalysisType.BUSINESS_MODEL, "시장 진입 전략", 7, 10,
            "판매 목표, 채널, 일정의 실행 가능성을 확인합니다.",
            BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS,
            BusinessPlanSectionCode.SCHEDULE_RISK),
        definition(DimensionCode.FINANCIAL_VIABILITY, AnalysisType.BUSINESS_MODEL, "재무 실행 가능성", 8, 12,
            "원가·매출·비용 가정의 완전성과 검증 가능성을 확인합니다.",
            BusinessPlanSectionCode.COST_PROFITABILITY,
            BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS),
        definition(DimensionCode.EXECUTION_CAPABILITY, AnalysisType.TECHNOLOGY_OPERATION, "실행 역량", 9, 6,
            "기술·생산·운영 계획과 주요 실행 위험을 확인합니다.",
            BusinessPlanSectionCode.TECHNOLOGY_PRODUCTION,
            BusinessPlanSectionCode.SCHEDULE_RISK),
        definition(DimensionCode.LEGAL_AND_REGULATORY, AnalysisType.TECHNOLOGY_OPERATION, "법률·규제", 10, 6,
            "법률 사전검토의 위험과 미확정 사항을 사업 실행 제약으로 연결합니다.",
            BusinessPlanSectionCode.LEGAL_PERMITS)
    );

    private FeasibilityDimensionCatalog() {}

    public static List<Definition> all() { return DEFINITIONS; }

    public static Definition get(DimensionCode code) {
        return DEFINITIONS.stream().filter(item -> item.code() == code).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown feasibility dimension"));
    }

    /** 묶음에 속한 차원을 카탈로그 순서대로. */
    public static List<Definition> byGroup(AnalysisType group) {
        return DEFINITIONS.stream().filter(item -> item.group() == group).toList();
    }

    /** 묶음의 가중치 합. 묶음 점수를 100점 척도로 정규화할 때 분모가 된다. */
    public static int groupWeight(AnalysisType group) {
        return byGroup(group).stream().mapToInt(Definition::weight).sum();
    }

    private static Definition definition(
        DimensionCode code, AnalysisType group, String displayName, int order, int weight,
        String description, BusinessPlanSectionCode... sections
    ) {
        return new Definition(code, group, displayName, order, weight, description,
            List.of(sections));
    }

    public record Definition(
        DimensionCode code, AnalysisType group, String displayName, int displayOrder, int weight,
        String description, List<BusinessPlanSectionCode> sourceSections
    ) {}
}
