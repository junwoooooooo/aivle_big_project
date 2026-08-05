package com.aivle.backend.finance.dto;

import java.util.List;

public record FinancialInputDTO(
    String productName,
    String category,
    double marketSizeTam,
    double cagr,
    double targetPrice,
    double unitCogs,
    double annualLaborCost,
    double annualOfficeCost,
    double annualServerCost,
    double initialInvestment,
    List<Integer> targetSalesQ,
    List<Integer> targetUsers,
    double cac,
    double monthlyChurnRate,
    double discountRate
) {}
