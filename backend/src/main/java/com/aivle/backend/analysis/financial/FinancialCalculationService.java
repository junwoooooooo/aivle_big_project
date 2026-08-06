package com.aivle.backend.analysis.financial;

import static com.aivle.backend.analysis.financial.FinancialModels.*;

import com.aivle.backend.analysis.financial.entity.RevenueModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Pure deterministic calculator: no repositories, HTTP context, random input, or AI dependency. */
@Component
public class FinancialCalculationService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public CalculationResult calculate(Assumptions input, int periodMonths, List<Scenario> scenarios) {
        List<ScenarioResult> results = scenarios.stream()
            .map(scenario -> scenario(input, periodMonths, scenario)).toList();
        ScenarioResult base = results.stream().filter(item -> "BASE".equals(item.code()))
            .findFirst().orElse(results.get(0));
        return new CalculationResult(results, sensitivity(input, periodMonths), summary(base));
    }

    public ScenarioResult scenario(Assumptions input, int periodMonths, Scenario scenario) {
        BigDecimal volumeMultiplier = multiplier(scenario.salesVolumeAdjustment());
        BigDecimal priceMultiplier = multiplier(scenario.priceAdjustment());
        BigDecimal variableMultiplier = multiplier(scenario.variableCostAdjustment());
        BigDecimal fixedMultiplier = multiplier(scenario.fixedCostAdjustment());
        BigDecimal fixed = sum(input.monthlyLaborCost(), input.monthlyMarketingCost(),
            input.monthlyInfrastructureCost(), input.monthlyRentCost(), input.monthlyOtherFixedCost())
            .multiply(fixedMultiplier);
        BigDecimal initial = sum(input.initialDevelopmentCost(), input.initialEquipmentCost(),
            input.initialMarketingCost(), input.initialOtherCost());
        BigDecimal cumulative = initial.negate();
        BigDecimal subscribers = value(input.initialSubscribers());
        List<MonthlyResult> months = new ArrayList<>();
        BigDecimal totalRevenue = ZERO, totalVariable = ZERO, totalFixed = ZERO, totalProfit = ZERO;
        BigDecimal minCash = cumulative;
        Integer payback = cumulative.signum() >= 0 ? 0 : null;
        for (int month = 1; month <= periodMonths; month++) {
            Revenue revenue = revenue(input, month, subscribers, volumeMultiplier, priceMultiplier);
            subscribers = revenue.endSubscribers();
            BigDecimal variable = revenue.variableBase().multiply(variableMultiplier)
                .add(revenue.amount().multiply(value(input.paymentFeeRate())).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            BigDecimal contribution = revenue.amount().subtract(variable);
            BigDecimal profit = contribution.subtract(fixed);
            cumulative = cumulative.add(profit);
            if (cumulative.compareTo(minCash) < 0) minCash = cumulative;
            if (payback == null && cumulative.signum() >= 0) payback = month;
            months.add(new MonthlyResult(month, money(revenue.volume()), money(revenue.amount()), money(variable),
                money(contribution), money(fixed), money(profit), money(cumulative), money(subscribers)));
            totalRevenue = totalRevenue.add(revenue.amount()); totalVariable = totalVariable.add(variable);
            totalFixed = totalFixed.add(fixed); totalProfit = totalProfit.add(profit);
        }
        BigDecimal unitMargin = unitMargin(input, priceMultiplier, variableMultiplier);
        String unavailable = unitMargin.signum() <= 0 ? "CONTRIBUTION_MARGIN_NON_POSITIVE" : null;
        BigDecimal breakUnits = unavailable == null ? fixed.divide(unitMargin, 2, RoundingMode.CEILING) : null;
        BigDecimal breakRevenue = unavailable == null ? breakUnits.multiply(effectiveUnitPrice(input, priceMultiplier)) : null;
        Integer breakMonth = unavailable == null ? months.stream().filter(item -> item.operatingProfit().signum() >= 0)
            .map(MonthlyResult::month).findFirst().orElse(null) : null;
        BigDecimal marginRate = totalRevenue.signum() == 0 ? ZERO : totalRevenue.subtract(totalVariable)
            .divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(HUNDRED);
        return new ScenarioResult(scenario.code(), scenario.label(), months, money(totalRevenue), money(totalVariable),
            money(totalFixed), money(totalProfit), percent(marginRate), breakUnits == null ? null : money(breakUnits),
            breakRevenue == null ? null : money(breakRevenue), breakMonth, payback, money(minCash),
            money(minCash.signum() < 0 ? minCash.negate() : ZERO), unavailable);
    }

    private Revenue revenue(Assumptions input, int month, BigDecimal subscribers,
                            BigDecimal volumeMultiplier, BigDecimal priceMultiplier) {
        BigDecimal oneTimeVolume = ZERO, oneTimeRevenue = ZERO, subscriptionRevenue = ZERO;
        BigDecimal subscriptionVariableBase = ZERO, endSubscribers = subscribers;
        if (input.revenueModel() != RevenueModel.SUBSCRIPTION) {
            BigDecimal growthMultiplier = ONE.add(
                value(input.monthlyGrowthRate()).divide(HUNDRED, 8, RoundingMode.HALF_UP)
            ).pow(month - 1);
            oneTimeVolume = value(input.monthlySalesVolume()).multiply(volumeMultiplier)
                .multiply(growthMultiplier);
            oneTimeRevenue = oneTimeVolume.multiply(effectiveUnitPrice(input, priceMultiplier));
        }
        if (input.revenueModel() != RevenueModel.ONE_TIME) {
            BigDecimal churn = subscribers.multiply(value(input.monthlyChurnRate())).divide(HUNDRED, 8, RoundingMode.HALF_UP);
            BigDecimal newSubscribers = value(input.monthlyNewSubscribers()).multiply(volumeMultiplier);
            endSubscribers = subscribers.add(newSubscribers).subtract(churn).max(ZERO);
            BigDecimal average = subscribers.add(endSubscribers).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            subscriptionRevenue = average.multiply(value(input.monthlySubscriptionPrice()).multiply(priceMultiplier));
            subscriptionVariableBase = average.multiply(value(input.unitVariableCost()).add(value(input.otherVariableCostPerUnit())));
        }
        BigDecimal variableBase = oneTimeVolume.multiply(value(input.unitVariableCost()).add(value(input.otherVariableCostPerUnit())))
            .add(subscriptionVariableBase);
        BigDecimal displayedVolume = input.revenueModel() == RevenueModel.ONE_TIME
            ? oneTimeVolume
            : input.revenueModel() == RevenueModel.SUBSCRIPTION
                ? subscribers.add(endSubscribers).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)
                : oneTimeVolume.add(subscribers.add(endSubscribers)
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        return new Revenue(displayedVolume, oneTimeRevenue.add(subscriptionRevenue), variableBase, endSubscribers);
    }

    private List<SensitivityPoint> sensitivity(Assumptions input, int months) {
        List<SensitivityPoint> values = new ArrayList<>();
        addSensitivity(values, input, months, "VOLUME", List.of(-20, -10, 0, 10, 20));
        addSensitivity(values, input, months, "PRICE", List.of(-10, 0, 10));
        addSensitivity(values, input, months, "VARIABLE_COST", List.of(0, 10, 20));
        addSensitivity(values, input, months, "FIXED_COST", List.of(0, 10, 20));
        return values;
    }

    private void addSensitivity(List<SensitivityPoint> target, Assumptions input, int months,
                                String variable, List<Integer> adjustments) {
        for (int adjustment : adjustments) {
            Scenario value = switch (variable) {
                case "VOLUME" -> new Scenario("S", "판매량", decimal(adjustment), ZERO, ZERO, ZERO);
                case "PRICE" -> new Scenario("S", "가격", ZERO, decimal(adjustment), ZERO, ZERO);
                case "VARIABLE_COST" -> new Scenario("S", "변동비", ZERO, ZERO, decimal(adjustment), ZERO);
                default -> new Scenario("S", "고정비", ZERO, ZERO, ZERO, decimal(adjustment));
            };
            ScenarioResult result = scenario(input, months, value);
            target.add(new SensitivityPoint(variable, value.label(), decimal(adjustment),
                result.totalOperatingProfit(), result.breakEvenMonth(), result.requiredWorkingCapital()));
        }
    }

    private Summary summary(ScenarioResult base) {
        boolean profitable = base.totalOperatingProfit().signum() >= 0;
        return new Summary(
            profitable ? "기준 시나리오에서 분석 기간 누적 영업손익이 흑자입니다." : "기준 시나리오의 누적 영업손익이 적자입니다.",
            profitable ? "PROFITABLE" : "LOSS_MAKING",
            base.breakEvenMonth() == null ? "분석 기간 내 월 손익분기 도달 여부를 확인할 수 없습니다." : base.breakEvenMonth() + "개월 차부터 월 손익이 양수입니다.",
            "최소 " + base.requiredWorkingCapital().toPlainString() + "원의 운영자금 여유를 확인하세요.",
            base.paybackMonth() == null ? "분석 기간 내 초기 투자 회수에 도달하지 못했습니다." : base.paybackMonth() + "개월 차에 누적 현금흐름이 양수로 전환됩니다.",
            List.of("판매량", "가격", "변동비", "고정비"),
            base.calculationUnavailableReason() == null ? List.of("판매량과 고정비 가정을 실제 지표로 검증하세요.") : List.of("공헌이익 단가가 0 이하라 손익분기점을 계산할 수 없습니다."),
            List.of("보수 시나리오의 판매량과 초기 운영자금을 우선 확인하세요."),
            List.of("사용자 가정과 구조화 계획의 근거를 정기적으로 갱신하세요."),
            "이 결과는 확인한 가정으로 계산한 예상값이며 회계·세무·투자 자문을 대체하지 않습니다."
        );
    }

    private BigDecimal unitMargin(Assumptions input, BigDecimal priceMultiplier, BigDecimal variableMultiplier) {
        BigDecimal price = input.revenueModel() == RevenueModel.SUBSCRIPTION
            ? value(input.monthlySubscriptionPrice()).multiply(priceMultiplier)
            : effectiveUnitPrice(input, priceMultiplier);
        BigDecimal cost = value(input.unitVariableCost()).add(value(input.otherVariableCostPerUnit())).multiply(variableMultiplier)
            .add(price.multiply(value(input.paymentFeeRate())).divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return price.subtract(cost);
    }
    private BigDecimal effectiveUnitPrice(Assumptions input, BigDecimal multiplier) { return value(input.unitPrice()).multiply(multiplier); }
    private BigDecimal multiplier(BigDecimal adjustment) { return ONE.add(value(adjustment).divide(HUNDRED, 8, RoundingMode.HALF_UP)); }
    private BigDecimal sum(BigDecimal... values) { BigDecimal total = ZERO; for (BigDecimal value : values) total = total.add(value(value)); return total; }
    private BigDecimal value(BigDecimal value) { return value == null ? ZERO : value; }
    private BigDecimal money(BigDecimal value) { return value.setScale(0, RoundingMode.HALF_UP); }
    private BigDecimal percent(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal decimal(int value) { return BigDecimal.valueOf(value); }
    private record Revenue(BigDecimal volume, BigDecimal amount, BigDecimal variableBase, BigDecimal endSubscribers) { }
}
