package com.aivle.backend.analysis.feasibility;

import com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.DimensionCode;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import java.util.List;

public final class FeasibilityDimensionCatalog {
    public static final String VERSION = "feasibility-catalog-v1";

    private static final List<Definition> DEFINITIONS = List.of(
        definition(DimensionCode.PROBLEM_AND_NEED, "문제와 필요", 1, 12,
            "해결하려는 문제와 필요가 구체적인지 확인합니다.",
            BusinessPlanSectionCode.BUSINESS_OVERVIEW),
        definition(DimensionCode.TARGET_CUSTOMER, "목표 고객", 2, 12,
            "고객 범위와 해결할 필요가 검증 가능한지 확인합니다.",
            BusinessPlanSectionCode.TARGET_CUSTOMER),
        definition(DimensionCode.MARKET_ATTRACTIVENESS, "시장 매력도", 3, 10,
            "시장 주장과 출처, 외부 검증 필요성을 확인합니다.",
            BusinessPlanSectionCode.MARKET_SIZE),
        definition(DimensionCode.COMPETITIVE_POSITION, "경쟁 포지션", 4, 10,
            "대안과 차별화 주장이 근거를 갖추었는지 확인합니다.",
            BusinessPlanSectionCode.COMPETITIVE_ANALYSIS),
        definition(DimensionCode.PRODUCT_SOLUTION_FIT, "제품·해결책 적합성", 5, 10,
            "제안한 제품·서비스가 문제와 고객에 연결되는지 확인합니다.",
            BusinessPlanSectionCode.PRODUCT_SERVICE),
        definition(DimensionCode.BUSINESS_MODEL, "비즈니스 모델", 6, 12,
            "수익 구조와 가치 전달 방식의 가정을 확인합니다.",
            BusinessPlanSectionCode.BUSINESS_MODEL),
        definition(DimensionCode.GO_TO_MARKET, "시장 진입 전략", 7, 10,
            "판매 목표, 채널, 일정의 실행 가능성을 확인합니다.",
            BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS,
            BusinessPlanSectionCode.SCHEDULE_RISK),
        definition(DimensionCode.FINANCIAL_VIABILITY, "재무 실행 가능성", 8, 12,
            "원가·매출·비용 가정의 완전성과 검증 가능성을 확인합니다.",
            BusinessPlanSectionCode.COST_PROFITABILITY,
            BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS),
        definition(DimensionCode.EXECUTION_CAPABILITY, "실행 역량", 9, 6,
            "기술·생산·운영 계획과 주요 실행 위험을 확인합니다.",
            BusinessPlanSectionCode.TECHNOLOGY_PRODUCTION,
            BusinessPlanSectionCode.SCHEDULE_RISK),
        definition(DimensionCode.LEGAL_AND_REGULATORY, "법률·규제", 10, 6,
            "법률 사전검토의 위험과 미확정 사항을 사업 실행 제약으로 연결합니다.",
            BusinessPlanSectionCode.LEGAL_PERMITS)
    );

    private FeasibilityDimensionCatalog() {}

    public static List<Definition> all() { return DEFINITIONS; }

    public static Definition get(DimensionCode code) {
        return DEFINITIONS.stream().filter(item -> item.code() == code).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown feasibility dimension"));
    }

    private static Definition definition(
        DimensionCode code, String displayName, int order, int weight,
        String description, BusinessPlanSectionCode... sections
    ) {
        return new Definition(code, displayName, order, weight, description, List.of(sections));
    }

    public record Definition(
        DimensionCode code, String displayName, int displayOrder, int weight,
        String description, List<BusinessPlanSectionCode> sourceSections
    ) {}
}
