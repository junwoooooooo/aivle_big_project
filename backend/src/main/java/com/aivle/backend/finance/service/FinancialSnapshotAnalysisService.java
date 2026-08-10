package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModels.Assumptions;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.entity.RevenueModel;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Converts the immutable pipeline snapshot into the calculator's KRW-only contract. */
@Service
public class FinancialSnapshotAnalysisService {
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final FinancialModuleService module;

    public FinancialSnapshotAnalysisService(FinancialModuleService module) {
        this.module = module;
    }

    public FinancialModuleResponse analyze(JsonNode snapshot) {
        JsonNode values = snapshot.path("values");
        JsonNode targets = values.path("threeYearTargets");
        String metric = targets.path("metric").asText("salesVolume");
        RevenueModel model = revenueModel(values.path("revenueModel").asText(), metric);
        BigDecimal yearOneTarget = targetForYear(targets, 1);
        BigDecimal monthlyTarget = yearOneTarget.divide(TWELVE, 4, java.math.RoundingMode.HALF_UP);
        BigDecimal cac = decimal(snapshot.path("calculatedCac"));

        Assumptions assumptions = new Assumptions(
            model,
            money(values, "unitPrice"),
            model == RevenueModel.SUBSCRIPTION ? BigDecimal.ZERO : monthlyTarget,
            BigDecimal.ZERO,
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
            model == RevenueModel.SUBSCRIPTION ? monthlyTarget : BigDecimal.ZERO,
            model == RevenueModel.SUBSCRIPTION ? monthlyTarget : BigDecimal.ZERO,
            decimal(values.path("monthlyChurnRate"))
        );
        return module.preview(new FinancialModuleRequest(assumptions, 36,
            FinancialModuleRequest.MoneyUnit.KRW, null, 2000, 15, 5, 10, null));
    }

    private BigDecimal targetForYear(JsonNode targets, int year) {
        for (JsonNode value : targets.path("years")) if (value.path("year").asInt() == year) return decimal(value.path("value"));
        return BigDecimal.ZERO;
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
}
