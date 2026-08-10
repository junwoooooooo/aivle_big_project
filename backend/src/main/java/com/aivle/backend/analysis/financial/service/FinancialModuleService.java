package com.aivle.backend.analysis.financial.service;

import com.aivle.backend.analysis.financial.dto.FinancialModels.*;
import com.aivle.backend.analysis.financial.dto.FinancialModuleRequest;
import com.aivle.backend.analysis.financial.dto.FinancialModuleResponse;
import com.aivle.backend.integration.ai.AiServerException;
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
        try { var value = ai.generate(Map.of("baseScenario", result.scenarios().stream().filter(s -> "BASE".equals(s.code())).findFirst().orElse(result.scenarios().get(0)), "monteCarlo", risk)); return new FinancialModuleResponse.ModuleReport(value.headline(), value.findings(), value.cautions(), value.recommendedActions(), value.disclaimer()); }
        catch (AiServerException exception) { return fallback; }
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
    private FinancialModuleResponse.ModuleReport report(ScenarioResult base, FinancialModuleResponse.MonteCarloSummary risk) {
        String headline = base.totalOperatingProfit().signum() >= 0 ? "Base scenario is profitable over the selected period." : "Base scenario remains loss-making over the selected period.";
        return new FinancialModuleResponse.ModuleReport(headline,
            List.of("Total revenue: " + base.totalRevenue().toPlainString() + " KRW", "Operating profit: " + base.totalOperatingProfit().toPlainString() + " KRW", "Required working capital: " + base.requiredWorkingCapital().toPlainString() + " KRW"),
            List.of("Monte Carlo loss probability: " + risk.lossProbabilityPercent().toPlainString() + "%", "P10/P50/P90 profit should be reviewed before funding decisions."),
            List.of("Validate price, volume and variable-cost assumptions with observed data.", "Use the conservative scenario for cash planning."),
            "This module is a planning simulation based on supplied assumptions, not investment advice or a revenue guarantee.");
    }
    private List<Scenario> defaults() { return List.of(new Scenario("CONSERVATIVE", "Conservative", BigDecimal.valueOf(-20), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN), new Scenario("BASE", "Base", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), new Scenario("OPTIMISTIC", "Optimistic", BigDecimal.valueOf(20), BigDecimal.ZERO, BigDecimal.valueOf(-5), BigDecimal.valueOf(-5))); }
}
