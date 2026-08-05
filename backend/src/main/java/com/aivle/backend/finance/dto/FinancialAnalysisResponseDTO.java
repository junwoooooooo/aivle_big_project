package com.aivle.backend.finance.dto;

import java.util.List;
import java.util.Map;

public record FinancialAnalysisResponseDTO(
    List<Double> revenues,
    List<Double> opIncomes,
    Map<String, ScenarioResult> scenarios,
    double successProbability,
    String aiCritiqueReport,
    FinancialSummary financialSummary,
    List<DataSourceReference> sources
) {
    public record ScenarioResult(
        List<Double> monthlyCash,
        List<Double> monthlyRev,
        List<Double> monthlyCost,
        double bep
    ) {}

    public record FinancialSummary(
        List<Long> revenues,
        List<Long> cogs,
        List<Long> grossProfits,
        List<Long> sga,
        List<Long> opIncomes,
        List<Long> nonOpIncomes,
        List<Long> netIncomes,
        List<Double> margins
    ) {}

    public record DataSourceReference(
        String title,
        String url,
        String relevanceNote
    ) {}
}
