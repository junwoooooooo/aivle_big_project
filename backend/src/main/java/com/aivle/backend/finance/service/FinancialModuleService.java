package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModels.*;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Deterministic finance engine. Provider narration is deliberately outside this service. */
@Service
public class FinancialModuleService {
    private final FinancialCalculationService calculator;
    private final FinancialInputScaler scaler;
    private final FinancialMonteCarloService monteCarlo;

    public FinancialModuleService(FinancialCalculationService calculator, FinancialInputScaler scaler,
            FinancialMonteCarloService monteCarlo) {
        this.calculator = calculator; this.scaler = scaler; this.monteCarlo = monteCarlo;
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
        return new FinancialModuleResponse(result, chart(base), annual(base), stress(result.scenarios()), simulation,
            fallbackReport(base, simulation, "NOT_REQUESTED", null),
            new FinancialModuleResponse.ScalingInfo(request.moneyUnit().name(), "KRW", scaler.multiplier(request.moneyUnit()),
                scaler.moneyFields(), "DB values are stored and retrieved in KRW; only UI input is converted at this boundary."));
    }

    public FinancialModuleResponse withAiReport(FinancialModuleResponse deterministic,
            FinancialModuleResponse.ModuleReport report) {
        return new FinancialModuleResponse(deterministic.calculation(), deterministic.cashFlowChart(),
            deterministic.annualProjections(), deterministic.stressScenarios(), deterministic.monteCarlo(),
            report, deterministic.scaling());
    }

    public FinancialModuleResponse withFailedFallback(FinancialModuleResponse deterministic, String safeReason) {
        ScenarioResult base = deterministic.calculation().scenarios().stream()
            .filter(s -> "BASE".equals(s.code())).findFirst().orElse(deterministic.calculation().scenarios().get(0));
        return withAiReport(deterministic, fallbackReport(base, deterministic.monteCarlo(), "FAILED", safeReason));
    }

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
    private FinancialModuleResponse.ModuleReport fallbackReport(ScenarioResult base,
            FinancialModuleResponse.MonteCarloSummary risk, String providerStatus, String safeReason) {
        String headline = base.totalOperatingProfit().signum() >= 0
            ? "기준 시나리오는 선택한 기간에 누적 흑자입니다."
            : "기준 시나리오는 선택한 기간에 누적 적자입니다.";
        return new FinancialModuleResponse.ModuleReport(headline,
            List.of("누적 매출: " + base.totalRevenue().toPlainString() + " KRW",
                "영업이익: " + base.totalOperatingProfit().toPlainString() + " KRW",
                "필요 운전자금: " + base.requiredWorkingCapital().toPlainString() + " KRW"),
            List.of("Monte Carlo 손실 확률: " + risk.lossProbabilityPercent().toPlainString() + "%",
                "자금 결정 전에 P10/P50/P90 손익 범위를 함께 확인하세요."),
            List.of("가격·판매량·변동비 가정을 관측 자료로 검증하세요.", "현금 계획에는 보수 시나리오를 사용하세요."),
            "이 결과는 입력 가정에 따른 계획 시뮬레이션이며 투자·회계·세무 자문이나 매출 보장이 아닙니다.",
            "SYSTEM_CALCULATION_FALLBACK", providerStatus, safeReason);
    }
    private List<Scenario> defaults() { return List.of(
        new Scenario("CONSERVATIVE", "보수", BigDecimal.valueOf(-20), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN),
        new Scenario("BASE", "기준", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        new Scenario("OPTIMISTIC", "낙관", BigDecimal.valueOf(20), BigDecimal.ZERO, BigDecimal.valueOf(-5), BigDecimal.valueOf(-5)));
    }
}
