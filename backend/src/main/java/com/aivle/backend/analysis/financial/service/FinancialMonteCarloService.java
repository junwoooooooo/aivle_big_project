package com.aivle.backend.analysis.financial.service;

import com.aivle.backend.analysis.financial.dto.FinancialModels.Assumptions;
import com.aivle.backend.analysis.financial.dto.FinancialModels.Scenario;
import com.aivle.backend.analysis.financial.dto.FinancialModuleResponse.MonteCarloSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.SplittableRandom;
import org.springframework.stereotype.Service;

/** Repeats perturbed base cases; it is risk simulation, not an AI prediction. */
@Service
public class FinancialMonteCarloService {
    private final FinancialCalculationService calculator;
    public FinancialMonteCarloService(FinancialCalculationService calculator) { this.calculator = calculator; }

    public MonteCarloSummary simulate(Assumptions input, int months, int count, int volumeVolatility,
                                      int priceVolatility, int costVolatility, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        ArrayList<BigDecimal> profits = new ArrayList<>(count);
        int losses = 0, paybacks = 0;
        for (int i = 0; i < count; i++) {
            Scenario scenario = new Scenario("SIM", "simulation", shock(random, volumeVolatility),
                shock(random, priceVolatility), shock(random, costVolatility), BigDecimal.ZERO);
            var result = calculator.scenario(input, months, scenario);
            profits.add(result.totalOperatingProfit());
            if (result.totalOperatingProfit().signum() < 0) losses++;
            if (result.paybackMonth() != null) paybacks++;
        }
        profits.sort(Comparator.naturalOrder());
        return new MonteCarloSummary(count, percentile(profits, .10), percentile(profits, .50), percentile(profits, .90),
            percent(losses, count), percent(paybacks, count), seed);
    }
    private BigDecimal shock(SplittableRandom random, int volatility) {
        // Box-Muller normal variate; capped at +/- 3 standard deviations to avoid implausible tail cases.
        double gaussian = Math.max(-3, Math.min(3, Math.sqrt(-2 * Math.log(Math.max(random.nextDouble(), 1e-12))) * Math.cos(2 * Math.PI * random.nextDouble())));
        return BigDecimal.valueOf(gaussian * volatility).setScale(4, RoundingMode.HALF_UP);
    }
    private BigDecimal percentile(ArrayList<BigDecimal> values, double percentile) { return values.get((int) Math.ceil(percentile * values.size()) - 1); }
    private BigDecimal percent(int numerator, int denominator) { return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP); }
}
