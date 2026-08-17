package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarketStrategySelectorTests {
    private final MarketStrategySelector selector = new MarketStrategySelector();

    @Test
    void exercisePartnerMatchingUsesPopulationStrategy() {
        var selected = selector.select(
            "동네 운동 파트너 매칭", "운동 부족 직장인과 활동량이 적은 지역 주민",
            "가까운 운동 파트너를 매칭", "월 9,900원 개인 구독형 앱");

        assertThat(selected.series()).isEqualTo("B");
        assertThat(selected.strategy()).isEqualTo("POPULATION_UNIT");
        assertThat(selected.reason()).contains("대상 인구", "단가");
    }

    @Test
    void b2bOperationsServiceUsesOrganizationStrategy() {
        var selected = selector.select(
            "매장 운영 자동화 SaaS", "소상공인 매장 운영자", "기업용 운영 소프트웨어",
            "매장당 월 구독");

        assertThat(selected.series()).isEqualTo("A");
        assertThat(selected.strategy()).isEqualTo("ORGANIZATION_UNIT");
        assertThat(selected.reason()).contains("사업체 수", "단가");
    }

    @Test
    void commerceMarketplaceUsesTransactionStrategy() {
        var selected = selector.select(
            "지역 특산품 거래 마켓플레이스", "판매자와 소비자", "상품 판매 중개",
            "거래 수수료 기반 커머스");

        assertThat(selected.series()).isEqualTo("C");
        assertThat(selected.strategy()).isEqualTo("TRANSACTION_VALUE");
        assertThat(selected.reason()).contains("거래액", "점유율");
    }

    @Test
    void unknownConceptDoesNotFallBackToTransactionValue() {
        assertThat(selector.select("새로운 문제 해결 서비스").series()).isEqualTo("D");
    }

    @Test
    void bicycleRentalAnalyticsForMunicipalitiesUsesOrganizationStrategy() {
        var selected = selector.select("스마트 킥포인트 - 데이터 분석 서비스",
            "AI 카메라 데이터로 자전거 대여와 관리 효율을 높인다",
            "자전거 대여 업체와 지자체의 B2B 서비스 계약");
        assertThat(selected.series()).isEqualTo("A");
        assertThat(selected.strategy()).isEqualTo("ORGANIZATION_UNIT");
    }
}
