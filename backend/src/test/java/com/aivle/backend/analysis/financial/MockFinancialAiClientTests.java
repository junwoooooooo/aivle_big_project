package com.aivle.backend.analysis.financial;

import com.aivle.backend.analysis.financial.entity.FinancialTypes.AssumptionSourceType;
import com.aivle.backend.integration.ai.financial.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 섹션 원문은 실제 예시 문서(펜타클 스탠드)의 문장을 그대로 쓴다 —
 * 인용 부분문자열 검증이 진짜 원문에서 성립하는지 보기 위함이다.
 */
class MockFinancialAiClientTests {
    private static final String BUSINESS_MODEL = """
        • 가격 책정 전략 및 소비자가:
        - 소비자가(MSRP): 38,000원 (얼리버드 펀딩가: 29,800원)
        - B2B 공급가: 19,000원 (최소 주문 수량 50개 이상 조건)
        """;
    private static final String COST = """
        • 원가 구조 (단위: 개당 기준):
        - 제조 원가 (알루미늄 아노다이징 + TPU 몰딩): 약 8,500원
        - 포장 및 물류비: 약 2,500원
        • 손익분기점 (BEP) 달성 시점:
        초기 사출 금형 개발비 약 2,000만 원 감안 시, 누적 판매량 1,500개 달성 시점 BEP 달성 예상.
        """;
    private static final String SALES = """
        • 연도별 매출 및 판매 수량 목표:
        - 1년 차: 8,000개 판매 / 매출액 약 2억 8천만 원
        """;

    private final MockFinancialAiClient client = new MockFinancialAiClient();

    private FinancialAiRequest request() {
        return new FinancialAiRequest(1L, 2L, 3L, FinancialPolicy.PROMPT_VERSION,
            FinancialPolicy.PROMPT,
            List.of(new FinancialAiRequest.Section("BUSINESS_MODEL", "비즈니스 모델", BUSINESS_MODEL),
                new FinancialAiRequest.Section("COST_PROFITABILITY", "원가·수익성", COST),
                new FinancialAiRequest.Section(
                    "SALES_GOALS_FINANCIAL_PROJECTIONS", "판매 목표·재무 추정", SALES)),
            List.of());
    }

    private FinancialAiResponse.Assumption assumption(FinancialAiResponse result, String key) {
        return result.assumptions().stream()
            .filter(item -> key.equals(item.key())).findFirst().orElseThrow();
    }

    @Test
    void isDeterministic() {
        assertThat(client.extract(request())).isEqualTo(client.extract(request()));
    }

    @Test
    void everyPlanQuoteIsASubstringOfItsSection() {
        var result = client.extract(request());
        String allText = BUSINESS_MODEL + COST + SALES;
        assertThat(result.assumptions())
            .filteredOn(item -> item.source().type() == AssumptionSourceType.PLAN)
            .isNotEmpty()
            .allSatisfy(item -> {
                assertThat(item.source().quote()).isNotBlank();
                assertThat(allText).contains(item.source().quote());
            });
        // 후보의 인용도 같은 규칙을 지킨다
        assertThat(result.assumptions())
            .flatExtracting(FinancialAiResponse.Assumption::candidates)
            .allSatisfy(candidate -> assertThat(allText).contains(candidate.quote()));
    }

    @Test
    void multiplePricesBecomeCandidatesInsteadOfOnePickedSilently() {
        var unitPrice = assumption(client.extract(request()), FinancialPolicy.UNIT_PRICE);
        // 소비자가·얼리버드가·B2B 공급가 셋 다 후보로 온다 — 고르는 건 사용자 몫
        assertThat(unitPrice.candidates()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(unitPrice.candidates())
            .extracting(FinancialAiResponse.Candidate::value)
            .contains(38_000.0, 29_800.0, 19_000.0);
    }

    @Test
    void revenueMismatchIsSurfacedNotResolved() {
        var result = client.extract(request());
        // 38,000 × 8,000 = 3.04억 vs 기획서의 2.8억
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.kind()).isEqualTo("REVENUE_MISMATCH");
            assertThat(conflict.options())
                .containsExactly("UNIT_TIMES_VOLUME", "STATED_REVENUE");
        });
    }

    @Test
    void missingFixedCostIsLeftForTheUserAndDiscountRateConfesses() {
        var result = client.extract(request());
        // 월 고정비는 기획서가 답하지 않는다 — 지어내지 않고 비워 둔다
        assertThat(result.assumptions())
            .extracting(FinancialAiResponse.Assumption::key)
            .doesNotContain(FinancialPolicy.MONTHLY_FIXED_COST);
        // 할인율은 기본값을 쓰되 왜 그런지 자수한다
        var discount = assumption(result, FinancialPolicy.DISCOUNT_RATE);
        assertThat(discount.source().type()).isEqualTo(AssumptionSourceType.DEFAULT);
        assertThat(discount.source().note()).contains("기획서에 할인율이 없어");
        assertThat(discount.value()).isEqualTo(FinancialPolicy.DEFAULT_DISCOUNT_RATE);
    }

    @Test
    void derivesRateAndVolumeFromThePlanNumbers() {
        var result = client.extract(request());
        // 변동원가율 = (8,500 + 2,500) / 38,000 ≈ 0.2895
        assertThat(assumption(result, FinancialPolicy.VARIABLE_COST_RATE).value())
            .isEqualTo(0.2895);
        // 월 판매량 = 8,000 / 12 — 균등 분해는 우리 규칙이므로 DEFAULT로 자수한다
        var volume = assumption(result, FinancialPolicy.MONTHLY_VOLUME);
        assertThat(volume.value()).isEqualTo(666.6667);
        assertThat(volume.source().type()).isEqualTo(AssumptionSourceType.DEFAULT);
        assertThat(volume.source().note()).contains("12로 균등 분해");
        // 초기 투자 = 2,000만 원
        assertThat(assumption(result, FinancialPolicy.INITIAL_INVESTMENT).value())
            .isEqualTo(20_000_000.0);
    }

    @Test
    void inventsNothingWhenSectionsAreEmpty() {
        var result = client.extract(new FinancialAiRequest(
            1L, 2L, 3L, FinancialPolicy.PROMPT_VERSION, FinancialPolicy.PROMPT,
            List.of(), List.of()));
        // 기본값(할인율) 하나만 남고 나머지는 만들지 않는다
        assertThat(result.assumptions()).singleElement()
            .satisfies(item -> assertThat(item.key()).isEqualTo(FinancialPolicy.DISCOUNT_RATE));
        assertThat(result.conflicts()).isEmpty();
    }
}
