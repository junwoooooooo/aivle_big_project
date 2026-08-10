package com.aivle.backend.analysis.financial.service;

import com.aivle.backend.analysis.financial.dto.FinancialModels.Assumptions;
import com.aivle.backend.analysis.financial.dto.FinancialModuleRequest.MoneyUnit;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts UI display denominations once at the API boundary; DB and calculation layers always use KRW. */
@Component
public class FinancialInputScaler {
    private static final List<String> MONEY_FIELDS = List.of("unitPrice", "unitVariableCost", "otherVariableCostPerUnit",
        "monthlyLaborCost", "monthlyMarketingCost", "monthlyInfrastructureCost", "monthlyRentCost",
        "monthlyOtherFixedCost", "initialDevelopmentCost", "initialEquipmentCost", "initialMarketingCost",
        "initialOtherCost", "monthlySubscriptionPrice");

    public Assumptions toKrw(Assumptions a, MoneyUnit unit) {
        BigDecimal m = multiplier(unit);
        return new Assumptions(a.revenueModel(), multiply(a.unitPrice(), m), a.monthlySalesVolume(), a.monthlyGrowthRate(),
            multiply(a.unitVariableCost(), m), a.paymentFeeRate(), multiply(a.otherVariableCostPerUnit(), m),
            multiply(a.monthlyLaborCost(), m), multiply(a.monthlyMarketingCost(), m), multiply(a.monthlyInfrastructureCost(), m),
            multiply(a.monthlyRentCost(), m), multiply(a.monthlyOtherFixedCost(), m), multiply(a.initialDevelopmentCost(), m),
            multiply(a.initialEquipmentCost(), m), multiply(a.initialMarketingCost(), m), multiply(a.initialOtherCost(), m),
            multiply(a.monthlySubscriptionPrice(), m), a.initialSubscribers(), a.monthlyNewSubscribers(), a.monthlyChurnRate());
    }
    public BigDecimal multiplier(MoneyUnit unit) { return switch (unit) { case KRW -> BigDecimal.ONE; case THOUSAND_KRW -> BigDecimal.valueOf(1_000); case MILLION_KRW -> BigDecimal.valueOf(1_000_000); }; }
    public List<String> moneyFields() { return MONEY_FIELDS; }
    private BigDecimal multiply(BigDecimal value, BigDecimal multiplier) { return value == null ? null : value.multiply(multiplier); }
}
