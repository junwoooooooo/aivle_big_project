package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModels.Assumptions;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.entity.RevenueModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Converts the immutable pipeline snapshot into the calculator's KRW-only contract. */
@Service
public class FinancialSnapshotAnalysisService {
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FinancialModuleService module;

    public FinancialSnapshotAnalysisService(FinancialModuleService module) {
        this.module = module;
    }

    public FinancialModuleResponse analyze(JsonNode snapshot) {
        JsonNode values = snapshot.path("values");
        JsonNode targets = values.path("threeYearTargets");
        String metric = targets.path("metric").asText("salesVolume");
        RevenueModel model = revenueModel(values.path("revenueModel").asText(), metric);
        ForecastTrajectory trajectory = trajectory(targets);
        BigDecimal cac = decimal(snapshot.path("calculatedCac"));

        Assumptions assumptions = new Assumptions(
            model,
            money(values, "unitPrice"),
            model == RevenueModel.SUBSCRIPTION ? BigDecimal.ZERO : trajectory.firstMonthTarget(),
            trajectory.monthlyGrowthRate(),
            money(values, "unitVariableCost"),
            BigDecimal.ZERO,
            sumMoney(values, "paymentFee", "partnerPayout", "shippingCost", "customerIncrementalInfraCost"),
            money(values, "annualFixedLaborCost").divide(TWELVE, 2, java.math.RoundingMode.HALF_UP),
            money(values, "totalMarketingCost").divide(TWELVE, 2, java.math.RoundingMode.HALF_UP),
            money(values, "annualFixedInfrastructureCost").divide(TWELVE, 2, java.math.RoundingMode.HALF_UP),
            money(values, "annualFixedRentAndManagementCost").divide(TWELVE, 2, java.math.RoundingMode.HALF_UP),
            money(values, "totalSalesCost").add(cac).divide(TWELVE, 2, java.math.RoundingMode.HALF_UP),
            money(values, "initialDevelopmentAndRnDCost"),
            money(values, "initialEquipmentAndInfrastructureCost"),
            BigDecimal.ZERO,
            money(values, "initialPatentAndLicensingCost"),
            money(values, "monthlySubscriptionPrice"),
            model == RevenueModel.SUBSCRIPTION ? trajectory.firstMonthTarget() : BigDecimal.ZERO,
            model == RevenueModel.SUBSCRIPTION ? trajectory.firstMonthTarget() : BigDecimal.ZERO,
            decimal(values.path("monthlyChurnRate"))
        );
        return withMarketFeasibility(module.preview(new FinancialModuleRequest(assumptions, 36,
            FinancialModuleRequest.MoneyUnit.KRW, null, 2000, 15, 5, 10, null)), snapshot.path("upstreamReferences").path("marketAnalysis"));
    }

    private BigDecimal targetForYear(JsonNode targets, int year) {
        for (JsonNode value : targets.path("years")) if (value.path("year").asInt() == year) return decimal(value.path("value"));
        return BigDecimal.ZERO;
    }

    /**
     * Converts the user's annual targets into a monthly time-series trajectory.
     * The first 12 months add up to the first-year target while the last target
     * determines the compounding pace through month 36. This avoids presenting
     * a flat line when the business plan contains growth targets.
     */
    private ForecastTrajectory trajectory(JsonNode targets) {
        BigDecimal yearOne = targetForYear(targets, 1);
        BigDecimal yearThree = targetForYear(targets, 3);
        if (yearOne.signum() <= 0) return new ForecastTrajectory(BigDecimal.ZERO, BigDecimal.ZERO);
        if (yearThree.signum() <= 0 || yearThree.compareTo(yearOne) == 0) {
            return new ForecastTrajectory(yearOne.divide(TWELVE, 4, java.math.RoundingMode.HALF_UP), BigDecimal.ZERO);
        }

        double ratio = yearThree.divide(yearOne, 12, java.math.RoundingMode.HALF_UP).doubleValue();
        double monthlyRate = Math.pow(ratio, 1d / 24d) - 1d;
        BigDecimal growthRate = BigDecimal.valueOf(monthlyRate).multiply(HUNDRED)
            .setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(monthlyRate));
        BigDecimal firstYearWeight = BigDecimal.ZERO;
        for (int month = 0; month < 12; month++) firstYearWeight = firstYearWeight.add(multiplier.pow(month));
        BigDecimal firstMonth = yearOne.divide(firstYearWeight, 4, java.math.RoundingMode.HALF_UP);
        return new ForecastTrajectory(firstMonth, growthRate);
    }

    /** Adds market-fit checks without changing the user's revenue forecast assumptions. */
    private FinancialModuleResponse withMarketFeasibility(FinancialModuleResponse result, JsonNode market) {
        BigDecimal tam = marketNumber(market.path("tam")); BigDecimal sam = marketNumber(market.path("sam"));
        BigDecimal growth = marketNumber(market.path("growth"));
        List<String> findings = new ArrayList<>(result.report() == null || result.report().findings() == null ? List.of() : result.report().findings());
        BigDecimal yearOneRevenue = annualRevenue(result, 1); BigDecimal yearThreeRevenue = annualRevenue(result, 3);
        if (sam != null && sam.signum() > 0 && yearThreeRevenue != null) {
            BigDecimal share = yearThreeRevenue.multiply(HUNDRED).divide(sam, 2, java.math.RoundingMode.HALF_UP);
            findings.add("3년 차 목표 매출은 SAM의 " + share.stripTrailingZeros().toPlainString() + "% 수준입니다.");
            if (share.compareTo(BigDecimal.valueOf(100)) > 0) findings.add("3년 차 목표 매출이 SAM을 초과합니다. 목표 고객 수·가격·시장 정의를 다시 확인하세요.");
        }
        if (tam != null && tam.signum() > 0 && yearThreeRevenue != null) {
            BigDecimal share = yearThreeRevenue.multiply(HUNDRED).divide(tam, 2, java.math.RoundingMode.HALF_UP);
            findings.add("3년 차 목표 매출은 TAM의 " + share.stripTrailingZeros().toPlainString() + "% 수준입니다.");
        }
        if (tam == null) findings.add("TAM: 시장조사에서 수치화된 전체 시장 규모 데이터가 없습니다.");
        if (sam == null) findings.add("SAM: 시장조사에서 수치화된 유효 시장 규모 데이터가 없습니다.");
        if (growth != null) findings.add("시장 분석 기준 시장 성장률은 " + growth.stripTrailingZeros().toPlainString() + "%입니다.");
        else findings.add("시장 성장률: 시장조사에서 수치화된 성장률 데이터가 없습니다.");
        if (growth != null && yearOneRevenue != null && yearOneRevenue.signum() > 0 && yearThreeRevenue != null) {
            BigDecimal targetGrowth = BigDecimal.valueOf((Math.pow(yearThreeRevenue.divide(yearOneRevenue, 12, java.math.RoundingMode.HALF_UP).doubleValue(), .5d) - 1d) * 100d)
                .setScale(2, java.math.RoundingMode.HALF_UP);
            findings.add("재무 목표의 연평균 매출 성장률은 " + targetGrowth.stripTrailingZeros().toPlainString() + "%로, 시장 성장률과 비교해 실행 난이도를 점검해야 합니다.");
        }
        var report = result.report();
        var localized = new FinancialModuleResponse.ModuleReport(report == null ? "시장 규모를 반영한 재무 분석 결과입니다." : report.headline(), findings,
            report == null ? List.of() : report.cautions(), report == null ? List.of() : report.recommendedActions(), report == null ? "입력 가정에 따른 계획 시뮬레이션입니다." : report.disclaimer());
        return new FinancialModuleResponse(result.calculation(), result.cashFlowChart(), result.annualProjections(), result.stressScenarios(), result.monteCarlo(), localized, result.scaling());
    }

    private BigDecimal annualRevenue(FinancialModuleResponse result, int year) {
        if (result.annualProjections() == null) return null;
        return result.annualProjections().stream().filter(value -> value.year() == year).map(FinancialModuleResponse.AnnualProjection::revenue).findFirst().orElse(null);
    }

    private BigDecimal marketNumber(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        if (value.isNumber()) return value.decimalValue();
        for (String key : List.of("amount", "value", "krw", "estimate", "percent", "rate")) {
            JsonNode candidate = value.path(key); if (candidate.isNumber()) return candidate.decimalValue();
            if (candidate.isTextual()) try { return new BigDecimal(candidate.asText().replaceAll("[^0-9.-]", "")); } catch (NumberFormatException ignored) { }
            if (candidate.isObject()) { BigDecimal nested = marketNumber(candidate); if (nested != null) return nested; }
        }
        return null;
    }
    private RevenueModel revenueModel(String value, String metric) {
        if ("SUBSCRIPTION".equals(value)) return RevenueModel.SUBSCRIPTION;
        if ("HYBRID".equals(value)) return RevenueModel.MIXED;
        if ("ONE_TIME".equals(value)) return RevenueModel.ONE_TIME;
        return "subscriberCount".equals(metric) ? RevenueModel.SUBSCRIPTION : RevenueModel.ONE_TIME;
    }
    private BigDecimal money(JsonNode values, String key) { return decimal(values.path(key).path("amount")); }
    private BigDecimal sumMoney(JsonNode values, String... keys) {
        BigDecimal total = BigDecimal.ZERO;
        for (String key : keys) total = total.add(money(values, key));
        return total;
    }
    private BigDecimal decimal(JsonNode value) { return value != null && value.isNumber() ? value.decimalValue() : BigDecimal.ZERO; }
    private record ForecastTrajectory(BigDecimal firstMonthTarget, BigDecimal monthlyGrowthRate) { }
}
