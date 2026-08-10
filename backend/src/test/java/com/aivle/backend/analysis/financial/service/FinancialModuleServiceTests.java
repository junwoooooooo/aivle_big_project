package com.aivle.backend.analysis.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.analysis.financial.service.FinancialCalculationService;
import com.aivle.backend.analysis.financial.dto.FinancialModels.Assumptions;
import com.aivle.backend.analysis.financial.entity.RevenueModel;
import com.aivle.backend.analysis.financial.dto.FinancialModuleRequest;
import com.aivle.backend.analysis.financial.service.FinancialInputScaler;
import com.aivle.backend.analysis.financial.service.FinancialModuleService;
import com.aivle.backend.analysis.financial.service.FinancialMonteCarloService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialModuleServiceTests {
    @Test
    void normalizesMillionWonInputAndReturnsDeterministicRiskDistribution() {
        var calculator = new FinancialCalculationService();
        var service = new FinancialModuleService(calculator, new FinancialInputScaler(), new FinancialMonteCarloService(calculator));
        var assumptions = new Assumptions(RevenueModel.ONE_TIME, bd(".01"), bd("100"), BigDecimal.ZERO, bd(".003"), BigDecimal.ZERO,
            BigDecimal.ZERO, bd(".2"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd(".5"), BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO);
        var request = new FinancialModuleRequest(assumptions, 12, FinancialModuleRequest.MoneyUnit.MILLION_KRW,
            null, 100, 10, 5, 5, 7L);

        var first = service.preview(request);
        var second = service.preview(request);

        assertThat(first.calculation().scenarios()).hasSize(3);
        assertThat(first.cashFlowChart()).hasSize(12);
        assertThat(first.scaling().multiplier()).isEqualByComparingTo("1000000");
        assertThat(first.scaling().databaseRule()).contains("KRW");
        assertThat(first.monteCarlo()).isEqualTo(second.monteCarlo());
    }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
