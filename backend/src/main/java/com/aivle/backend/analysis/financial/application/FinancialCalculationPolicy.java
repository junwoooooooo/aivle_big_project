package com.aivle.backend.analysis.financial.application;

import com.aivle.backend.analysis.financial.entity.FinancialTypes.UnavailableReason;
import com.aivle.backend.analysis.financial.entity.FinancialTypes.Verdict;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 확정된 가정으로 재무 지표를 계산한다. 순수 함수 — AI도 저장소도 건드리지 않는다.
 * (FeasibilityScorePolicy와 같은 자리: 계산·판정은 백엔드 결정론, 서술만 AI.)
 *
 * <p><b>계산 불가는 예외가 아니라 결과다.</b> 공헌이익이 음수거나 손익분기에 도달하지 못하는 것은
 * 이 사업의 사실이지 오류가 아니므로, 지표를 null로 두고 사유 코드를 함께 실어 돌려준다.
 * 화면이 그 사유를 사람 말로 번역한다.
 *
 * <p>모든 금액은 원 단위 double이다. 표시 반올림은 표현 계층의 몫이며 여기서는 하지 않는다.
 */
@Component
public class FinancialCalculationPolicy {
    /** 손익분기·회수 판단에 쓰는 기간. NPV도 같은 창을 본다. */
    public static final int HORIZON_MONTHS = 36;
    /** 안전 여유율이 이 아래면 목표 미달에 취약하다고 본다. */
    private static final double SAFETY_MARGIN_WARN = 0.20;
    private static final int IRR_ITERATIONS = 200;
    private static final double IRR_LOWER = -0.99;
    /** 연 10,000%. 회수가 한두 달인 극단 가정에서도 부호가 바뀌도록 넉넉히 잡는다. */
    private static final double IRR_UPPER = 100.0;

    public Outcome evaluate(Inputs inputs) {
        List<String> missing = inputs.missingKeys();

        Metric<Double> contribution = contributionMargin(inputs, missing);
        Metric<Double> monthlyProfit = monthlyProfit(inputs, contribution, missing);
        Metric<Double> breakEvenQty = breakEvenQty(inputs, contribution, missing);
        Metric<Double> safetyMargin = safetyMargin(inputs, breakEvenQty, missing);
        Metric<Integer> breakEvenMonth = breakEvenMonth(inputs, monthlyProfit, missing);
        Metric<Double> roi3y = roi(inputs, monthlyProfit, missing);
        Metric<Double> npv = npv(inputs, monthlyProfit, missing);
        Metric<Double> irr = irr(inputs, monthlyProfit, missing);
        Metric<PeakFunding> peak = peakFunding(inputs, monthlyProfit, missing);

        return new Outcome(contribution, monthlyProfit, breakEvenQty, breakEvenMonth,
            safetyMargin, roi3y, npv, irr, peak,
            monthlyCashFlow(inputs, monthlyProfit),
            verdict(contribution, breakEvenMonth, safetyMargin, missing));
    }

    // ------------------------------------------------------------------ 지표

    /** 건당 공헌이익 = 객단가 × (1 − 변동원가율). 이게 0 이하면 팔수록 손해다. */
    private Metric<Double> contributionMargin(Inputs inputs, List<String> missing) {
        if (inputs.unitPrice() == null || inputs.variableCostRate() == null) {
            return Metric.missing(missing);
        }
        return Metric.of(inputs.unitPrice() * (1.0 - inputs.variableCostRate()));
    }

    /** 월 영업이익 = 공헌이익 × 월 판매량 − 월 고정비. */
    private Metric<Double> monthlyProfit(
        Inputs inputs, Metric<Double> contribution, List<String> missing
    ) {
        if (contribution.isUnavailable() || inputs.monthlyVolume() == null
            || inputs.monthlyFixedCost() == null) {
            return Metric.missing(missing);
        }
        return Metric.of(contribution.value() * inputs.monthlyVolume() - inputs.monthlyFixedCost());
    }

    /** 손익분기 수량/월 = 고정비 / 공헌이익. */
    private Metric<Double> breakEvenQty(
        Inputs inputs, Metric<Double> contribution, List<String> missing
    ) {
        if (contribution.isUnavailable() || inputs.monthlyFixedCost() == null) {
            return Metric.missing(missing);
        }
        if (contribution.value() <= 0) {
            return Metric.unavailable(UnavailableReason.NON_POSITIVE_CONTRIBUTION);
        }
        return Metric.of(inputs.monthlyFixedCost() / contribution.value());
    }

    /** 안전 여유율 = (계획 판매량 − 손익분기 수량) / 계획 판매량. 음수면 목표가 BEP에 못 미친다. */
    private Metric<Double> safetyMargin(
        Inputs inputs, Metric<Double> breakEvenQty, List<String> missing
    ) {
        if (breakEvenQty.isUnavailable()) {
            return breakEvenQty.propagate(missing);
        }
        if (inputs.monthlyVolume() == null || inputs.monthlyVolume() == 0) {
            return Metric.missing(missing);
        }
        return Metric.of((inputs.monthlyVolume() - breakEvenQty.value()) / inputs.monthlyVolume());
    }

    /** 손익분기 시점 = ceil(초기투자 / 월 영업이익). 월이익이 0 이하면 도달하지 못한다. */
    private Metric<Integer> breakEvenMonth(
        Inputs inputs, Metric<Double> monthlyProfit, List<String> missing
    ) {
        if (monthlyProfit.isUnavailable() || inputs.initialInvestment() == null) {
            return Metric.missing(missing);
        }
        if (monthlyProfit.value() <= 0) {
            return Metric.unavailable(UnavailableReason.NON_POSITIVE_MONTHLY_PROFIT);
        }
        return Metric.of((int) Math.ceil(inputs.initialInvestment() / monthlyProfit.value()));
    }

    /** 3년 ROI = (36개월 누적이익 − 초기투자) / 초기투자. */
    private Metric<Double> roi(
        Inputs inputs, Metric<Double> monthlyProfit, List<String> missing
    ) {
        if (monthlyProfit.isUnavailable() || inputs.initialInvestment() == null
            || inputs.initialInvestment() == 0) {
            return Metric.missing(missing);
        }
        double cumulative = monthlyProfit.value() * HORIZON_MONTHS;
        return Metric.of((cumulative - inputs.initialInvestment()) / inputs.initialInvestment());
    }

    /** NPV = −초기투자 + Σ 월이익/(1+r/12)^t. 할인율은 결측 시 기본값이 주입돼 들어온다. */
    private Metric<Double> npv(
        Inputs inputs, Metric<Double> monthlyProfit, List<String> missing
    ) {
        if (monthlyProfit.isUnavailable() || inputs.initialInvestment() == null
            || inputs.discountRate() == null) {
            return Metric.missing(missing);
        }
        return Metric.of(netPresentValue(
            inputs.initialInvestment(), monthlyProfit.value(), inputs.discountRate()));
    }

    /**
     * IRR = NPV가 0이 되는 연 할인율. 이분법으로 근사한다.
     * 부호 변화가 없으면(전 구간 양수이거나 전 구간 음수) 해가 없으므로 사유와 함께 비운다.
     */
    private Metric<Double> irr(
        Inputs inputs, Metric<Double> monthlyProfit, List<String> missing
    ) {
        if (monthlyProfit.isUnavailable() || inputs.initialInvestment() == null) {
            return Metric.missing(missing);
        }
        double investment = inputs.initialInvestment();
        double profit = monthlyProfit.value();
        double low = netPresentValue(investment, profit, IRR_LOWER);
        double high = netPresentValue(investment, profit, IRR_UPPER);
        if (low == 0) return Metric.of(IRR_LOWER);
        if (high == 0) return Metric.of(IRR_UPPER);
        if (low > 0 == high > 0) {
            return Metric.unavailable(UnavailableReason.NO_SIGN_CHANGE);
        }
        double lo = IRR_LOWER;
        double hi = IRR_UPPER;
        for (int i = 0; i < IRR_ITERATIONS; i++) {
            double mid = (lo + hi) / 2;
            double value = netPresentValue(investment, profit, mid);
            if (netPresentValue(investment, profit, lo) > 0 == value > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return Metric.of((lo + hi) / 2);
    }

    /**
     * 최대 자금 필요 시점 = 누적 현금흐름의 최저점(금액·월).
     * 월이익이 양수면 0개월차(초기투자 직후)가 최저점이고, 음수면 계속 깊어져 마지막 달이 최저점이다.
     */
    private Metric<PeakFunding> peakFunding(
        Inputs inputs, Metric<Double> monthlyProfit, List<String> missing
    ) {
        if (monthlyProfit.isUnavailable() || inputs.initialInvestment() == null) {
            return Metric.missing(missing);
        }
        double[] cumulative = cumulativeCashFlow(
            inputs.initialInvestment(), monthlyProfit.value());
        int worstMonth = 0;
        for (int month = 1; month < cumulative.length; month++) {
            if (cumulative[month] < cumulative[worstMonth]) {
                worstMonth = month;
            }
        }
        return Metric.of(new PeakFunding(cumulative[worstMonth], worstMonth));
    }

    /** 화면의 누적 현금흐름 차트가 그대로 쓰는 시계열(0~36개월). */
    private List<Double> monthlyCashFlow(Inputs inputs, Metric<Double> monthlyProfit) {
        if (monthlyProfit.isUnavailable() || inputs.initialInvestment() == null) {
            return List.of();
        }
        List<Double> series = new ArrayList<>(HORIZON_MONTHS + 1);
        for (double value : cumulativeCashFlow(inputs.initialInvestment(), monthlyProfit.value())) {
            series.add(value);
        }
        return List.copyOf(series);
    }

    // ------------------------------------------------------------------ 판정

    private Verdict verdict(
        Metric<Double> contribution, Metric<Integer> breakEvenMonth,
        Metric<Double> safetyMargin, List<String> missing
    ) {
        if (!missing.isEmpty() && contribution.isUnavailable()) {
            return Verdict.INSUFFICIENT_INFORMATION;
        }
        if (!contribution.isUnavailable() && contribution.value() <= 0) {
            return Verdict.HIGH_RISK;
        }
        if (breakEvenMonth.reason() == UnavailableReason.NON_POSITIVE_MONTHLY_PROFIT) {
            return Verdict.HIGH_RISK;
        }
        if (breakEvenMonth.isUnavailable()) {
            return Verdict.INSUFFICIENT_INFORMATION;
        }
        boolean slowPayback = breakEvenMonth.value() > HORIZON_MONTHS;
        boolean thinMargin = !safetyMargin.isUnavailable()
            && safetyMargin.value() < SAFETY_MARGIN_WARN;
        return slowPayback || thinMargin ? Verdict.CONDITIONAL : Verdict.PROMISING;
    }

    // ------------------------------------------------------------------ 보조

    private double netPresentValue(double investment, double monthlyProfit, double annualRate) {
        double monthlyRate = annualRate / 12.0;
        double total = -investment;
        for (int month = 1; month <= HORIZON_MONTHS; month++) {
            total += monthlyProfit / Math.pow(1 + monthlyRate, month);
        }
        return total;
    }

    private double[] cumulativeCashFlow(double investment, double monthlyProfit) {
        double[] cumulative = new double[HORIZON_MONTHS + 1];
        cumulative[0] = -investment;
        for (int month = 1; month <= HORIZON_MONTHS; month++) {
            cumulative[month] = cumulative[month - 1] + monthlyProfit;
        }
        return cumulative;
    }

    // ------------------------------------------------------------------ 계약

    /**
     * 확정된 가정. 아직 확정되지 않은 키는 null로 들어오고, 그 키 이름이 {@code missingKeys}에 담긴다.
     * 비율은 소수다(변동원가율 0.3 = 30%, 할인율 0.1 = 연 10%).
     */
    public record Inputs(
        Double unitPrice,
        Double variableCostRate,
        Double monthlyVolume,
        Double monthlyFixedCost,
        Double initialInvestment,
        Double discountRate,
        List<String> missingKeys
    ) {
        public Inputs {
            missingKeys = missingKeys == null ? List.of() : List.copyOf(missingKeys);
        }
    }

    /** 지표 하나. 값이 없으면 왜 없는지가 함께 온다. */
    public record Metric<T>(T value, UnavailableReason reason, List<String> missingKeys) {
        public Metric {
            missingKeys = missingKeys == null ? List.of() : List.copyOf(missingKeys);
        }

        static <T> Metric<T> of(T value) {
            return new Metric<>(value, null, List.of());
        }

        static <T> Metric<T> missing(List<String> missingKeys) {
            return new Metric<>(null, UnavailableReason.MISSING_ASSUMPTION, missingKeys);
        }

        static <T> Metric<T> unavailable(UnavailableReason reason) {
            return new Metric<>(null, reason, List.of());
        }

        /** 상류 지표가 비었을 때 그 사유를 그대로 물려받는다. */
        <R> Metric<R> propagate(List<String> missingKeys) {
            return new Metric<>(null, reason,
                reason == UnavailableReason.MISSING_ASSUMPTION ? missingKeys : List.of());
        }

        public boolean isUnavailable() {
            return value == null;
        }
    }

    public record PeakFunding(double amount, int month) {}

    public record Outcome(
        Metric<Double> contributionMargin,
        Metric<Double> monthlyProfit,
        Metric<Double> breakEvenQty,
        Metric<Integer> breakEvenMonth,
        Metric<Double> safetyMarginPct,
        Metric<Double> roi3y,
        Metric<Double> npv36m,
        Metric<Double> irr,
        Metric<PeakFunding> peakFunding,
        List<Double> cumulativeCashFlow,
        Verdict verdict
    ) {}
}
