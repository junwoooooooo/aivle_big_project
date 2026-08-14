package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModels.*;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FinancialModuleService {
    private final FinancialCalculationService calculator;
    private final FinancialInputScaler scaler;
    private final FinancialMonteCarloService monteCarlo;
    private final FinancialAiReportClient ai;
    @Autowired
    public FinancialModuleService(FinancialCalculationService calculator, FinancialInputScaler scaler, FinancialMonteCarloService monteCarlo, FinancialAiReportClient ai) {
        this.calculator = calculator; this.scaler = scaler; this.monteCarlo = monteCarlo; this.ai = ai;
    }
    FinancialModuleService(FinancialCalculationService calculator, FinancialInputScaler scaler, FinancialMonteCarloService monteCarlo) {
        this(calculator, scaler, monteCarlo, null);
    }
    public FinancialModuleResponse preview(FinancialModuleRequest request) {
        Assumptions assumptions = scaler.toKrw(request.assumptions(), request.moneyUnit());
        List<Scenario> scenarios = request.scenarios() == null || request.scenarios().isEmpty() ? defaults() : request.scenarios();
        CalculationResult result = calculator.calculate(assumptions, request.periodMonths(), scenarios);
        ScenarioResult base = result.scenarios().stream().filter(s -> "BASE".equals(s.code())).findFirst().orElse(result.scenarios().get(0));
        long seed = request.randomSeed() == null ? 20260810L : request.randomSeed();
        var simulation = monteCarlo.simulate(assumptions, request.periodMonths(), request.simulationCount() == null ? 1000 : request.simulationCount(),
            request.volumeVolatilityPercent() == null ? 15 : request.volumeVolatilityPercent(), request.priceVolatilityPercent() == null ? 5 : request.priceVolatilityPercent(),
            request.costVolatilityPercent() == null ? 10 : request.costVolatilityPercent(), seed);
        var fallback = report(base, simulation);
        var report = aiReport(result, simulation, fallback);
        return new FinancialModuleResponse(result, chart(base), annual(base), stress(result.scenarios()), simulation, report, new FinancialModuleResponse.ScalingInfo(request.moneyUnit().name(), "KRW", scaler.multiplier(request.moneyUnit()), scaler.moneyFields(), "DB values are stored and retrieved in KRW; only UI input is converted at this boundary."));
    }
    private FinancialModuleResponse.ModuleReport aiReport(CalculationResult result, FinancialModuleResponse.MonteCarloSummary risk, FinancialModuleResponse.ModuleReport fallback) {
        if (ai == null) return fallback;
        try {
            var value = ai.generate(Map.of("baseScenario", result.scenarios().stream().filter(s -> "BASE".equals(s.code())).findFirst().orElse(result.scenarios().get(0)), "monteCarlo", risk));
            if (!containsKorean(value.headline())) return fallback;
            return new FinancialModuleResponse.ModuleReport(value.headline(), value.findings(), value.cautions(), value.recommendedActions(), value.disclaimer());
        }
        catch (RuntimeException exception) { return fallback; }
    }
    private boolean containsKorean(String value) { return value != null && value.matches(".*[가-힣].*"); }
    private List<FinancialModuleResponse.ChartPoint> chart(ScenarioResult result) { return result.months().stream().map(m -> new FinancialModuleResponse.ChartPoint(m.month(), m.revenue(), m.operatingProfit(), m.cumulativeCashFlow())).toList(); }
    private List<FinancialModuleResponse.AnnualProjection> annual(ScenarioResult base) {
        List<FinancialModuleResponse.AnnualProjection> rows = new ArrayList<>();
        for (int start = 0; start < base.months().size(); start += 12) {
            var months = base.months().subList(start, Math.min(start + 12, base.months().size()));
            BigDecimal revenue = months.stream().map(MonthlyResult::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal variable = months.stream().map(MonthlyResult::variableCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fixed = months.stream().map(MonthlyResult::fixedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal profit = months.stream().map(MonthlyResult::operatingProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal grossProfit = revenue.subtract(variable);
            BigDecimal nonOperatingIncome = BigDecimal.ZERO;
            BigDecimal taxableIncome = profit.add(nonOperatingIncome);
            BigDecimal corporateTax = taxableIncome.signum() > 0 ? taxableIncome.multiply(BigDecimal.valueOf(.20)).setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal netIncome = taxableIncome.subtract(corporateTax);
            BigDecimal margin = revenue.signum() == 0 ? BigDecimal.ZERO : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
            rows.add(new FinancialModuleResponse.AnnualProjection(start / 12 + 1, revenue, variable, grossProfit, fixed, profit, nonOperatingIncome, corporateTax, netIncome, margin));
        }
        return rows;
    }
    private List<FinancialModuleResponse.StressScenario> stress(List<ScenarioResult> scenarios) { return scenarios.stream().map(s -> new FinancialModuleResponse.StressScenario(s.code(), s.label(), s.breakEvenMonth(), s.totalOperatingProfit(), s.requiredWorkingCapital(), chart(s))).toList(); }
    private FinancialModuleResponse.ModuleReport report(ScenarioResult base, FinancialModuleResponse.MonteCarloSummary risk) {
        String headline = base.totalOperatingProfit().signum() >= 0 ? "기준 시나리오에서는 분석 기간 내 수익성이 확인됩니다." : "기준 시나리오에서는 분석 기간 내 누적 영업손실이 예상됩니다.";
        return new FinancialModuleResponse.ModuleReport(headline,
            List.of("총 예상 매출: " + base.totalRevenue().toPlainString() + " KRW", "총 영업이익: " + base.totalOperatingProfit().toPlainString() + " KRW", "필요 운전자금: " + base.requiredWorkingCapital().toPlainString() + " KRW"),
            List.of("몬테카를로 분석상 손실 확률: " + risk.lossProbabilityPercent().toPlainString() + "%", "투자 판단 전 P10·P50·P90 수익 범위를 함께 확인해야 합니다."),
            List.of("실제 관측 데이터로 가격·판매량·변동비 가정을 검증하세요.", "현금 계획은 보수적 시나리오를 기준으로 수립하세요."),
            "이 분석은 입력한 가정에 따른 계획 시뮬레이션이며 투자 조언이나 매출 보장이 아닙니다.");
    }
    private List<Scenario> defaults() { return List.of(new Scenario("CONSERVATIVE", "Conservative", BigDecimal.valueOf(-20), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN), new Scenario("BASE", "Base", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), new Scenario("OPTIMISTIC", "Optimistic", BigDecimal.valueOf(20), BigDecimal.ZERO, BigDecimal.valueOf(-5), BigDecimal.valueOf(-5))); }
}
