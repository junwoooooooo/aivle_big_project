package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.PlanSectionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public final class BusinessPlanSectionCatalog {
    private final List<BusinessPlanSectionDefinition> definitions;
    private final Map<BusinessPlanSectionCode, BusinessPlanSectionDefinition> byCode;

    public BusinessPlanSectionCatalog() {
        this.definitions = List.of(
                definition(
                        BusinessPlanSectionCode.BUSINESS_OVERVIEW,
                        "사업 개요",
                        1,
                        "사업의 개요를 설명하는 항목",
                        List.of("Business Overview", "사업소개"),
                        Set.of(PlanSectionType.OVERVIEW)
                ),
                definition(
                        BusinessPlanSectionCode.MARKET_SIZE,
                        "시장 규모",
                        2,
                        "대상 시장의 규모를 설명하는 항목",
                        List.of("Market Size", "시장성"),
                        Set.of(PlanSectionType.MARKET)
                ),
                definition(
                        BusinessPlanSectionCode.TARGET_CUSTOMER,
                        "타겟 고객",
                        3,
                        "핵심 고객 집단을 설명하는 항목",
                        List.of("Target Customer", "목표 고객", "고객 세그먼트"),
                        Set.of(PlanSectionType.TARGET_CUSTOMER)
                ),
                definition(
                        BusinessPlanSectionCode.COMPETITIVE_ANALYSIS,
                        "경쟁 분석",
                        4,
                        "경쟁 환경과 비교 대상을 설명하는 항목",
                        List.of("Competitive Analysis", "경쟁사 분석"),
                        Set.of(PlanSectionType.COMPETITION)
                ),
                definition(
                        BusinessPlanSectionCode.PRODUCT_SERVICE,
                        "제품 · 서비스",
                        5,
                        "제공할 제품 또는 서비스를 설명하는 항목",
                        List.of("Product / Service", "제품 서비스"),
                        Set.of(PlanSectionType.PRODUCT_SERVICE)
                ),
                definition(
                        BusinessPlanSectionCode.BUSINESS_MODEL,
                        "비즈니스 모델",
                        6,
                        "가치 제공과 수익 창출 구조를 설명하는 항목",
                        List.of("Business Model", "BM"),
                        Set.of(PlanSectionType.BUSINESS_MODEL)
                ),
                definition(
                        BusinessPlanSectionCode.COST_PROFITABILITY,
                        "원가 · 수익성",
                        7,
                        "원가 구조와 수익성을 설명하는 항목",
                        List.of("Cost & Profitability", "원가 수익성"),
                        Set.of(PlanSectionType.FINANCIAL)
                ),
                definition(
                        BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS,
                        "판매 목표 · 재무 추정",
                        8,
                        "판매 목표와 재무 추정치를 설명하는 항목",
                        List.of("Sales Goals & Financials", "매출 목표", "재무 전망"),
                        Set.of(PlanSectionType.FINANCIAL)
                ),
                definition(
                        BusinessPlanSectionCode.TECHNOLOGY_PRODUCTION,
                        "기술 · 생산",
                        9,
                        "기술 구현과 생산·운영 방식을 설명하는 항목",
                        List.of("Technology & Production", "기술 생산"),
                        Set.of(PlanSectionType.TECHNOLOGY_OPERATION)
                ),
                definition(
                        BusinessPlanSectionCode.LEGAL_PERMITS,
                        "법률 · 인허가",
                        10,
                        "관련 법률과 인허가 요구를 설명하는 항목",
                        List.of("Legal & Compliance", "법률 규제", "인허가"),
                        Set.of(PlanSectionType.LEGAL_REGULATION)
                ),
                definition(
                        BusinessPlanSectionCode.SCHEDULE_RISK,
                        "일정 · 리스크",
                        11,
                        "추진 일정과 주요 위험을 설명하는 항목",
                        List.of("Schedule & Risk Management", "일정 위험"),
                        Set.of(PlanSectionType.SCHEDULE, PlanSectionType.RISK)
                ),
                definition(
                        BusinessPlanSectionCode.EVIDENCE_LIST,
                        "근거 자료 목록",
                        12,
                        "주장을 뒷받침하는 근거 자료를 열거하는 항목",
                        List.of("References", "근거 목록", "출처"),
                        Set.of(PlanSectionType.EVIDENCE)
                )
        );

        EnumMap<BusinessPlanSectionCode, BusinessPlanSectionDefinition> index =
                new EnumMap<>(BusinessPlanSectionCode.class);
        for (BusinessPlanSectionDefinition definition : definitions) {
            if (index.put(definition.code(), definition) != null) {
                throw new IllegalStateException("duplicate business plan section code");
            }
        }
        if (definitions.size() != BusinessPlanSectionCode.values().length) {
            throw new IllegalStateException("business plan section catalog is incomplete");
        }
        this.byCode = Map.copyOf(index);
    }

    public List<BusinessPlanSectionDefinition> all() {
        return definitions;
    }

    public Optional<BusinessPlanSectionDefinition> find(BusinessPlanSectionCode code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public BusinessPlanSectionDefinition require(BusinessPlanSectionCode code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("unknown section code"));
    }

    private static BusinessPlanSectionDefinition definition(
            BusinessPlanSectionCode code,
            String displayName,
            int sequence,
            String description,
            List<String> aliases,
            Set<PlanSectionType> mappedTypes
    ) {
        return new BusinessPlanSectionDefinition(
                code,
                displayName,
                sequence,
                true,
                description,
                aliases,
                AllowedMissingPolicy.USER_INPUT_REQUIRED,
                mappedTypes
        );
    }
}
