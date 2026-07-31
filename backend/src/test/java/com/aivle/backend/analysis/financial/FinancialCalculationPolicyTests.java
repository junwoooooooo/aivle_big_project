package com.aivle.backend.analysis.financial;

import com.aivle.backend.analysis.financial.application.FinancialCalculationPolicy;
import com.aivle.backend.analysis.financial.application.FinancialCalculationPolicy.Inputs;
import com.aivle.backend.analysis.financial.entity.FinancialTypes.UnavailableReason;
import com.aivle.backend.analysis.financial.entity.FinancialTypes.Verdict;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 기준 케이스는 프론트 {@code financialViewModel.test.js}와 **같은 수치를 공유**한다.
 * 두 구현(백엔드 정책 / 프론트 what-if)이 어긋나면 여기와 저기가 함께 깨져야 한다.
 */
class FinancialCalculationPolicyTests {
    private final FinancialCalculationPolicy policy = new FinancialCalculationPolicy();

    /**
     * 기준 케이스 — 펜타클 예시의 실제 수치를 따른다:
     * 객단가 38,000(소비자가) · 변동원가율 0.29(원가 11,000) · 월 1,000개 ·
     * 월 고정비 2,000만 · 초기투자 5,000만 · 할인율 10%.
     */
    private Inputs base() {
        return new Inputs(38_000.0, 0.29, 1_000.0, 20_000_000.0, 50_000_000.0, 0.10, List.of());
    }

    @Test
    void computesTheBaseCaseEndToEnd() {
        var result = policy.evaluate(base());

        // 공헌이익 = 38,000 × (1 − 0.29) = 26,980
        assertThat(result.contributionMargin().value()).isEqualTo(26_980.0);
        // 월이익 = 26,980 × 1,000 − 20,000,000 = 6,980,000
        assertThat(result.monthlyProfit().value()).isEqualTo(6_980_000.0);
        // 손익분기 수량 = 20,000,000 / 26,980 ≈ 741.3개
        assertThat(result.breakEvenQty().value()).isCloseTo(741.29, within(0.01));
        // 안전 여유율 = (1,000 − 741.29) / 1,000 ≈ 25.9%
        assertThat(result.safetyMarginPct().value()).isCloseTo(0.2587, within(0.001));
        // 손익분기 시점 = ceil(50,000,000 / 6,980,000) = 8개월
        assertThat(result.breakEvenMonth().value()).isEqualTo(8);
        // 3년 ROI = (6,980,000 × 36 − 50,000,000) / 50,000,000 ≈ 4.026
        assertThat(result.roi3y().value()).isCloseTo(4.0256, within(0.001));
        assertThat(result.verdict()).isEqualTo(Verdict.PROMISING);
    }

    @Test
    void npvDiscountsFutureProfitAndIrrSolvesToZero() {
        var result = policy.evaluate(base());

        // 할인하지 않은 누적(251.28M − 50M)보다 작고, 여전히 양수다
        assertThat(result.npv36m().value())
            .isLessThan(6_980_000.0 * 36 - 50_000_000)
            .isGreaterThan(0);

        // IRR을 할인율로 되먹이면 NPV가 0이어야 한다 (이분법 근사의 정의)
        double irr = result.irr().value();
        var atIrr = policy.evaluate(new Inputs(
            38_000.0, 0.29, 1_000.0, 20_000_000.0, 50_000_000.0, irr, List.of()));
        assertThat(atIrr.npv36m().value()).isCloseTo(0.0, within(1.0));
    }

    @Test
    void sellingAtALossIsAResultNotAnError() {
        // 변동원가율 1.1 → 공헌이익 음수. 팔수록 손해라 손익분기가 존재하지 않는다.
        var result = policy.evaluate(new Inputs(
            38_000.0, 1.10, 1_000.0, 20_000_000.0, 50_000_000.0, 0.10, List.of()));

        assertThat(result.contributionMargin().value()).isNegative();
        assertThat(result.breakEvenQty().isUnavailable()).isTrue();
        assertThat(result.breakEvenQty().reason())
            .isEqualTo(UnavailableReason.NON_POSITIVE_CONTRIBUTION);
        assertThat(result.verdict()).isEqualTo(Verdict.HIGH_RISK);
    }

    @Test
    void zeroContributionAlsoBlocksBreakEven() {
        var result = policy.evaluate(new Inputs(
            38_000.0, 1.00, 1_000.0, 20_000_000.0, 50_000_000.0, 0.10, List.of()));
        assertThat(result.contributionMargin().value()).isZero();
        assertThat(result.breakEvenQty().reason())
            .isEqualTo(UnavailableReason.NON_POSITIVE_CONTRIBUTION);
        assertThat(result.verdict()).isEqualTo(Verdict.HIGH_RISK);
    }

    @Test
    void fixedCostAboveContributionMeansPaybackNeverArrives() {
        // 공헌이익은 양수지만 고정비가 더 커서 월이익이 음수 → 회수 불가
        var result = policy.evaluate(new Inputs(
            38_000.0, 0.29, 100.0, 20_000_000.0, 50_000_000.0, 0.10, List.of()));

        assertThat(result.monthlyProfit().value()).isNegative();
        assertThat(result.breakEvenMonth().reason())
            .isEqualTo(UnavailableReason.NON_POSITIVE_MONTHLY_PROFIT);
        assertThat(result.verdict()).isEqualTo(Verdict.HIGH_RISK);
        // 손익분기 수량 자체는 여전히 계산된다 — "얼마나 팔아야 하는가"는 답할 수 있다
        assertThat(result.breakEvenQty().value()).isCloseTo(741.29, within(0.01));
        // 안전 여유율은 음수 = 계획 판매량이 손익분기에 못 미친다
        assertThat(result.safetyMarginPct().value()).isNegative();
    }

    @Test
    void peakFundingIsTheInitialOutlayWhenProfitIsPositive() {
        var result = policy.evaluate(base());
        assertThat(result.peakFunding().value().amount()).isEqualTo(-50_000_000.0);
        assertThat(result.peakFunding().value().month()).isZero();
        assertThat(result.cumulativeCashFlow()).hasSize(37);
        assertThat(result.cumulativeCashFlow().get(0)).isEqualTo(-50_000_000.0);
    }

    @Test
    void peakFundingKeepsSinkingWhenMonthlyProfitIsNegative() {
        var result = policy.evaluate(new Inputs(
            38_000.0, 0.29, 100.0, 20_000_000.0, 50_000_000.0, 0.10, List.of()));
        // 계속 적자면 마지막 달이 가장 깊다 — 자금이 가장 많이 필요한 시점
        assertThat(result.peakFunding().value().month())
            .isEqualTo(FinancialCalculationPolicy.HORIZON_MONTHS);
        assertThat(result.peakFunding().value().amount()).isLessThan(-50_000_000.0);
    }

    @Test
    void missingAssumptionBlanksDependentMetricsAndNamesWhatToFill() {
        // 월 고정비는 기획서에서 나오지 않는 것이 정상 — 사용자가 채우기 전 상태
        var result = policy.evaluate(new Inputs(
            38_000.0, 0.29, 1_000.0, null, 50_000_000.0, 0.10, List.of("MONTHLY_FIXED_COST")));

        // 고정비와 무관한 공헌이익은 살아 있다
        assertThat(result.contributionMargin().value()).isEqualTo(26_980.0);
        // 고정비에 기대는 지표만 비고, 무엇을 채우면 되는지 함께 온다
        assertThat(result.breakEvenQty().isUnavailable()).isTrue();
        assertThat(result.breakEvenQty().reason()).isEqualTo(UnavailableReason.MISSING_ASSUMPTION);
        assertThat(result.breakEvenQty().missingKeys()).containsExactly("MONTHLY_FIXED_COST");
        assertThat(result.breakEvenMonth().isUnavailable()).isTrue();
        assertThat(result.peakFunding().isUnavailable()).isTrue();
        assertThat(result.verdict()).isEqualTo(Verdict.INSUFFICIENT_INFORMATION);
        // 0으로 채우지 않는다
        assertThat(result.cumulativeCashFlow()).isEmpty();
    }

    @Test
    void thinSafetyMarginDowngradesToConditional() {
        // 손익분기 수량 741.29 대비 판매량 800 → 여유율 7.3% (<20%)
        var result = policy.evaluate(new Inputs(
            38_000.0, 0.29, 800.0, 20_000_000.0, 50_000_000.0, 0.10, List.of()));
        assertThat(result.safetyMarginPct().value()).isLessThan(0.20);
        assertThat(result.monthlyProfit().value()).isPositive();
        assertThat(result.verdict()).isEqualTo(Verdict.CONDITIONAL);
    }

    @Test
    void slowPaybackAlsoDowngradesToConditional() {
        // 초기투자가 커서 회수에 36개월을 넘긴다
        var result = policy.evaluate(new Inputs(
            38_000.0, 0.29, 1_000.0, 20_000_000.0, 400_000_000.0, 0.10, List.of()));
        assertThat(result.breakEvenMonth().value())
            .isGreaterThan(FinancialCalculationPolicy.HORIZON_MONTHS);
        assertThat(result.verdict()).isEqualTo(Verdict.CONDITIONAL);
    }
}
