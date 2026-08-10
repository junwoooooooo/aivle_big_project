package com.aivle.backend.analysis.financial;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.analysis.financial.entity.RevenueModel;
import com.aivle.backend.analysis.financial.dto.FinancialModels;
import com.aivle.backend.analysis.financial.service.FinancialCalculationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialCalculationServiceTests {
    private final FinancialCalculationService calculator = new FinancialCalculationService();

    @Test
    void oneTimeCalculatesTwelveMonthsAndAllCoreTotals() {
        var result = calculator.scenario(oneTime(), 12, base());
        assertThat(result.months()).hasSize(12);
        assertThat(result.totalRevenue()).isEqualByComparingTo("12000000");
        assertThat(result.totalVariableCost()).isEqualByComparingTo("4560000");
        assertThat(result.totalFixedCost()).isEqualByComparingTo("3600000");
        assertThat(result.totalOperatingProfit()).isEqualByComparingTo("3840000");
        assertThat(result.months().get(0).cumulativeCashFlow()).isEqualByComparingTo("-180000");
        assertThat(result.paybackMonth()).isEqualTo(2);
        assertThat(result.requiredWorkingCapital()).isEqualByComparingTo("500000");
    }

    @Test
    void supportsTwentyFourAndThirtySixMonthPeriods() {
        assertThat(calculator.scenario(oneTime(), 24, base()).months()).hasSize(24);
        assertThat(calculator.scenario(oneTime(), 36, base()).months()).hasSize(36);
    }

    @Test
    void appliesGrowthAndPaymentFeeDeterministically() {
        var input = assumptions(RevenueModel.ONE_TIME, "10000", "100", "10", "3000", "5",
            "0", "0", "0", "0");
        var first = calculator.scenario(input, 12, base());
        var second = calculator.scenario(input, 12, base());
        assertThat(first.months().get(1).salesVolume()).isEqualByComparingTo("110");
        assertThat(first.months().get(0).variableCost()).isEqualByComparingTo("400000");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void subscriptionUsesAverageActiveSubscribersAndChurn() {
        var input = assumptions(RevenueModel.SUBSCRIPTION, null, null, "0", "1000", "0",
            "10000", "100", "20", "10");
        var result = calculator.scenario(input, 12, base());
        assertThat(result.months().get(0).activeSubscribers()).isEqualByComparingTo("110");
        assertThat(result.months().get(0).salesVolume()).isEqualByComparingTo("105");
        assertThat(result.months().get(0).revenue()).isEqualByComparingTo("1050000");
        assertThat(result.months().get(0).variableCost()).isEqualByComparingTo("157500");
    }

    @Test
    void subscriptionGrowthRateScalesMonthlyNewSubscribers() {
        var input = assumptions(RevenueModel.SUBSCRIPTION, null, null, "10", "1000", "0",
            "10000", "100", "20", "0");
        var result = calculator.scenario(input, 12, base());
        assertThat(result.months().get(0).activeSubscribers()).isEqualByComparingTo("120");
        assertThat(result.months().get(1).activeSubscribers()).isGreaterThan(result.months().get(0).activeSubscribers());
    }

    @Test
    void hundredPercentChurnDoesNotCreateNegativeSubscribers() {
        var input = assumptions(RevenueModel.SUBSCRIPTION, null, null, "0", "1000", "0",
            "10000", "100", "0", "100");
        var result = calculator.scenario(input, 12, base());
        assertThat(result.months().get(0).activeSubscribers()).isZero();
        assertThat(result.months().get(1).activeSubscribers()).isZero();
    }

    @Test
    void mixedAddsOneTimeAndSubscriptionRevenue() {
        var input = assumptions(RevenueModel.MIXED, "10000", "10", "0", "1000", "0",
            "5000", "20", "0", "0");
        var result = calculator.scenario(input, 12, base());
        assertThat(result.months().get(0).revenue()).isEqualByComparingTo("200000");
        assertThat(result.months().get(0).salesVolume()).isEqualByComparingTo("30");
    }

    @Test
    void conservativeBaseAndOptimisticScenariosRemainOrdered() {
        var result = calculator.calculate(oneTime(), 12, scenarios());
        assertThat(result.scenarios()).extracting(item -> item.code())
            .containsExactly("CONSERVATIVE", "BASE", "OPTIMISTIC");
        assertThat(result.scenarios().get(0).totalOperatingProfit())
            .isLessThan(result.scenarios().get(1).totalOperatingProfit());
        assertThat(result.scenarios().get(2).totalOperatingProfit())
            .isGreaterThan(result.scenarios().get(1).totalOperatingProfit());
    }

    @Test
    void breakEvenIsUnavailableForZeroOrNegativeUnitMargin() {
        var zero = assumptions(RevenueModel.ONE_TIME, "100", "10", "0", "100", "0",
            null, null, null, null);
        var negative = assumptions(RevenueModel.ONE_TIME, "100", "10", "0", "101", "0",
            null, null, null, null);
        for (var input : List.of(zero, negative)) {
            var result = calculator.scenario(input, 12, base());
            assertThat(result.calculationUnavailableReason()).isEqualTo("CONTRIBUTION_MARGIN_NON_POSITIVE");
            assertThat(result.breakEvenUnits()).isNull();
            assertThat(result.breakEvenRevenue()).isNull();
        }
    }

    @Test
    void roundsMoneyHalfUpAtWonBoundary() {
        var input = assumptions(RevenueModel.ONE_TIME, "100.5", "1", "0", "0", "0",
            null, null, null, null);
        assertThat(calculator.scenario(input, 12, base()).months().get(0).revenue())
            .isEqualByComparingTo("101");
    }

    @Test
    void sensitivityContainsVolumePriceVariableAndFixedCostCases() {
        var result = calculator.calculate(oneTime(), 12, scenarios());
        assertThat(result.sensitivity()).hasSize(14);
        assertThat(result.sensitivity()).extracting(item -> item.variable())
            .contains("VOLUME", "PRICE", "VARIABLE_COST", "FIXED_COST");
        assertThat(result.sensitivity()).allSatisfy(item -> {
            assertThat(item.totalOperatingProfit()).isNotNull();
            assertThat(item.requiredWorkingCapital()).isNotNull();
        });
    }

    @Test
    void explicitZeroRemainsARealValueInCalculation() {
        var input = new FinancialModels.Assumptions(
            RevenueModel.ONE_TIME, decimal("10000"), BigDecimal.ONE, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null, BigDecimal.ZERO
        );
        var result = calculator.scenario(input, 12, base());
        assertThat(result.totalVariableCost()).isZero();
        assertThat(result.totalRevenue()).isPositive();
    }

    private static FinancialModels.Assumptions oneTime() {
        return assumptions(RevenueModel.ONE_TIME, "10000", "100", "0", "3000", "3",
            null, null, null, null);
    }

    private static FinancialModels.Assumptions assumptions(
        RevenueModel model, String unitPrice, String volume, String growth, String unitCost,
        String fee, String subscriptionPrice, String initialSubscribers,
        String newSubscribers, String churn
    ) {
        return new FinancialModels.Assumptions(
            model, decimal(unitPrice), decimal(volume), decimal(growth), decimal(unitCost),
            decimal(fee), decimal("500"), decimal("200000"), decimal("100000"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, decimal("500000"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, decimal(subscriptionPrice),
            decimal(initialSubscribers), decimal(newSubscribers), decimal(churn)
        );
    }

    private static List<FinancialModels.Scenario> scenarios() {
        return List.of(
            scenario("CONSERVATIVE", -20, 0, 10, 10),
            scenario("BASE", 0, 0, 0, 0),
            scenario("OPTIMISTIC", 20, 0, -5, -5)
        );
    }

    private static FinancialModels.Scenario base() {
        return scenario("BASE", 0, 0, 0, 0);
    }

    private static FinancialModels.Scenario scenario(String code, int volume, int price, int variable, int fixed) {
        return new FinancialModels.Scenario(code, code, decimal(volume), decimal(price), decimal(variable), decimal(fixed));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value);
    }
}
