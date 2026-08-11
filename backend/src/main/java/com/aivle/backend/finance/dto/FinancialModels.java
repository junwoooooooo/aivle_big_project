package com.aivle.backend.finance.dto;

import com.aivle.backend.finance.entity.RevenueModel;
import java.math.BigDecimal;
import java.util.List;

/** Transport-neutral calculation values. Money is always KRW rounded with HALF_UP. */
public final class FinancialModels {
    private FinancialModels() { }

    public record Assumptions(
        RevenueModel revenueModel,
        BigDecimal unitPrice,
        BigDecimal monthlySalesVolume,
        BigDecimal monthlyGrowthRate,
        BigDecimal unitVariableCost,
        BigDecimal paymentFeeRate,
        BigDecimal otherVariableCostPerUnit,
        BigDecimal monthlyLaborCost,
        BigDecimal monthlyMarketingCost,
        BigDecimal monthlyInfrastructureCost,
        BigDecimal monthlyRentCost,
        BigDecimal monthlyOtherFixedCost,
        BigDecimal initialDevelopmentCost,
        BigDecimal initialEquipmentCost,
        BigDecimal initialMarketingCost,
        BigDecimal initialOtherCost,
        BigDecimal monthlySubscriptionPrice,
        BigDecimal initialSubscribers,
        BigDecimal monthlyNewSubscribers,
        BigDecimal monthlyChurnRate
    ) { }

    public record Scenario(
        String code,
        String label,
        BigDecimal salesVolumeAdjustment,
        BigDecimal priceAdjustment,
        BigDecimal variableCostAdjustment,
        BigDecimal fixedCostAdjustment
    ) { }

    public record MonthlyResult(
        int month,
        BigDecimal salesVolume,
        BigDecimal revenue,
        BigDecimal variableCost,
        BigDecimal contributionMargin,
        BigDecimal fixedCost,
        BigDecimal operatingProfit,
        BigDecimal cumulativeCashFlow,
        BigDecimal activeSubscribers
    ) { }

    public record ScenarioResult(
        String code,
        String label,
        List<MonthlyResult> months,
        BigDecimal totalRevenue,
        BigDecimal totalVariableCost,
        BigDecimal totalFixedCost,
        BigDecimal totalOperatingProfit,
        BigDecimal contributionMarginRate,
        BigDecimal breakEvenUnits,
        BigDecimal breakEvenRevenue,
        Integer breakEvenMonth,
        Integer paybackMonth,
        BigDecimal minimumCashBalance,
        BigDecimal requiredWorkingCapital,
        String calculationUnavailableReason
    ) { }

    public record SensitivityPoint(String variable, String label, BigDecimal adjustment,
                                   BigDecimal totalOperatingProfit, Integer breakEvenMonth,
                                   BigDecimal requiredWorkingCapital) { }

    public record CalculationResult(List<ScenarioResult> scenarios,
                                    List<SensitivityPoint> sensitivity,
                                    Summary summary) { }

    public record Summary(String headline, String profitabilityStatus, String breakEvenSummary,
                          String cashRiskSummary, String paybackSummary,
                          List<String> sensitiveAssumptions, List<String> keyRisks,
                          List<String> recommendedActions, List<String> missingEvidence,
                          String disclaimer) { }

    public record MissingField(String code, String label, String reason) { }
}
