package com.aivle.backend.jaemu;

import java.util.List;

public record JaemuAnalysisResponse(
    String productName,
    String category,
    List<YearlyResult> yearly,
    List<Scenario> scenarios,
    List<Double> npvDistribution,
    Metrics metrics,
    Report report
) {
    public record YearlyResult(int year, long salesQuantity, long revenue, long cogs, long grossProfit,
                               long sellingGeneralAdmin, long operatingIncome, long netIncome,
                               double operatingMargin) { }
    public record Scenario(String name, List<Long> monthlyCash, List<Long> monthlyRevenue,
                           List<Long> monthlyCost, int breakEvenMonth) { }
    public record Metrics(double successProbability, double npv, int breakEvenMonth, long fixedAnnualCost,
                          double demandFactor, double retentionFactor) { }
    public record Report(String grade, List<String> highlights, List<String> actions, List<String> assumptions) { }
}
