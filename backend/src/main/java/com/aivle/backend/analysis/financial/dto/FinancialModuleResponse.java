package com.aivle.backend.analysis.financial.dto;

import com.aivle.backend.analysis.financial.dto.FinancialModels.CalculationResult;
import java.math.BigDecimal;
import java.util.List;

/** UI-ready response: chart series, deterministic calculation, risk distribution and report narrative. */
public record FinancialModuleResponse(
    CalculationResult calculation,
    List<ChartPoint> cashFlowChart,
    List<AnnualProjection> annualProjections,
    List<StressScenario> stressScenarios,
    MonteCarloSummary monteCarlo,
    ModuleReport report,
    ScalingInfo scaling
) {
    public record ChartPoint(int month, BigDecimal revenue, BigDecimal operatingProfit, BigDecimal cumulativeCashFlow) { }
    public record AnnualProjection(int year, BigDecimal revenue, BigDecimal variableCost,
                                   BigDecimal grossProfit, BigDecimal sellingGeneralAdministrative,
                                   BigDecimal operatingProfit, BigDecimal nonOperatingIncome,
                                   BigDecimal corporateTax, BigDecimal netIncome,
                                   BigDecimal operatingMarginPercent) { }
    public record StressScenario(String code, String label, Integer breakEvenMonth,
                                 BigDecimal totalOperatingProfit, BigDecimal requiredWorkingCapital,
                                 List<ChartPoint> monthlyCashFlow) { }
    public record MonteCarloSummary(int simulations, BigDecimal profitP10, BigDecimal profitP50,
                                    BigDecimal profitP90, BigDecimal lossProbabilityPercent,
                                    BigDecimal paybackProbabilityPercent, Long seed) { }
    public record ModuleReport(String headline, List<String> findings, List<String> cautions,
                               List<String> recommendedActions, String disclaimer) { }
    public record ScalingInfo(String inputMoneyUnit, String calculationMoneyUnit, BigDecimal multiplier,
                              List<String> scaledFields, String databaseRule) { }
}
