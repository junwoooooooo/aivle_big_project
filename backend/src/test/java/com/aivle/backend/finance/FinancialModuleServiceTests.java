package com.aivle.backend.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.finance.dto.FinancialModels.Assumptions;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.entity.RevenueModel;
import com.aivle.backend.finance.service.FinancialCalculationService;
import com.aivle.backend.finance.service.FinancialInputScaler;
import com.aivle.backend.finance.service.FinancialModuleService;
import com.aivle.backend.finance.service.FinancialMonteCarloService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialModuleServiceTests {
    @Test void scalingAndSeededMonteCarloRemainDeterministic() {
        var calculator = new FinancialCalculationService();
        var service = new FinancialModuleService(calculator, new FinancialInputScaler(), new FinancialMonteCarloService(calculator));
        var assumptions = new Assumptions(RevenueModel.ONE_TIME, bd(".01"), bd("100"), BigDecimal.ZERO, bd(".003"),
            BigDecimal.ZERO, BigDecimal.ZERO, bd(".2"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd(".5"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO);
        var request = new FinancialModuleRequest(assumptions, 12, FinancialModuleRequest.MoneyUnit.MILLION_KRW,
            null, 100, 10, 5, 5, 7L);
        var first = service.preview(request); var second = service.preview(request);
        assertThat(first.calculation().scenarios()).hasSize(3);
        assertThat(first.cashFlowChart()).hasSize(12);
        assertThat(first.scaling().multiplier()).isEqualByComparingTo("1000000");
        assertThat(first.scaling().databaseRule()).contains("KRW");
        assertThat(first.monteCarlo()).isEqualTo(second.monteCarlo());
        assertThat(first.monteCarlo().seed()).isEqualTo(7L);
        assertThat(first.monteCarlo().profitP10()).isLessThanOrEqualTo(first.monteCarlo().profitP50());
        assertThat(first.monteCarlo().profitP50()).isLessThanOrEqualTo(first.monteCarlo().profitP90());
        assertThat(first.monteCarlo().lossProbabilityPercent()).isBetween(BigDecimal.ZERO, bd("100"));
        assertThat(first.monteCarlo().paybackProbabilityPercent()).isBetween(BigDecimal.ZERO, bd("100"));
        assertThat(first.monteCarlo().simulations()).isEqualTo(100);
        assertThat(first.annualProjections()).isNotEmpty();
        assertThat(first.annualProjections().get(0).corporateTax()).isNotNull();
        assertThat(first.annualProjections().get(0).netIncome()).isNotNull();
    }

    @Test void krwThousandAndMillionBoundariesScaleEveryMoneyFieldOnlyOnce() {
        var scaler = new FinancialInputScaler();
        var base = new Assumptions(RevenueModel.ONE_TIME, bd("1.25"), bd("2"), BigDecimal.ZERO, bd(".5"),
            bd("3"), bd(".25"), bd("4"), bd("5"), bd("6"), bd("7"), bd("8"), bd("9"), bd("10"),
            bd("11"), bd("12"), bd("13"), bd("14"), bd("15"), bd("16"));
        assertThat(scaler.toKrw(base, FinancialModuleRequest.MoneyUnit.KRW).unitPrice()).isEqualByComparingTo("1.25");
        assertThat(scaler.toKrw(base, FinancialModuleRequest.MoneyUnit.THOUSAND_KRW).unitPrice()).isEqualByComparingTo("1250");
        assertThat(scaler.toKrw(base, FinancialModuleRequest.MoneyUnit.MILLION_KRW).unitPrice()).isEqualByComparingTo("1250000");
        assertThat(scaler.toKrw(base, FinancialModuleRequest.MoneyUnit.MILLION_KRW).monthlySalesVolume())
            .isEqualByComparingTo("2");
    }

    @Test void zeroAndMaximumVolatilityStayBoundedAndReproducible() {
        var calculator = new FinancialCalculationService();
        var monteCarlo = new FinancialMonteCarloService(calculator);
        var assumptions = new Assumptions(RevenueModel.ONE_TIME, bd("10000"), bd("100"), BigDecimal.ZERO,
            bd("3000"), bd("3"), bd("500"), bd("200000"), bd("100000"), BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, bd("500000"), BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, null, null, null, BigDecimal.ZERO);
        var zero = monteCarlo.simulate(assumptions, 12, 100, 0, 0, 0, 19L);
        var maximum = monteCarlo.simulate(assumptions, 12, 100, 100, 100, 100, 19L);
        assertThat(zero.profitP10()).isEqualByComparingTo(zero.profitP90());
        assertThat(maximum.profitP10()).isLessThanOrEqualTo(maximum.profitP50());
        assertThat(maximum.profitP50()).isLessThanOrEqualTo(maximum.profitP90());
        assertThat(maximum.lossProbabilityPercent()).isBetween(BigDecimal.ZERO, bd("100"));
        assertThat(maximum.paybackProbabilityPercent()).isBetween(BigDecimal.ZERO, bd("100"));
        assertThat(maximum).isEqualTo(monteCarlo.simulate(assumptions, 12, 100, 100, 100, 100, 19L));
    }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
