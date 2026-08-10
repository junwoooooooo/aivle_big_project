package com.aivle.backend.analysis.financial.dto;

import com.aivle.backend.analysis.financial.dto.FinancialModels.Assumptions;
import com.aivle.backend.analysis.financial.dto.FinancialModels.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Public module contract. Monetary values are converted to KRW before calculation. */
public record FinancialModuleRequest(
    @Valid @NotNull Assumptions assumptions,
    @NotNull @Min(12) @Max(60) Integer periodMonths,
    @NotNull MoneyUnit moneyUnit,
    @Valid List<Scenario> scenarios,
    @Min(100) @Max(10000) Integer simulationCount,
    @Min(0) @Max(100) Integer volumeVolatilityPercent,
    @Min(0) @Max(100) Integer priceVolatilityPercent,
    @Min(0) @Max(100) Integer costVolatilityPercent,
    Long randomSeed
) {
    public enum MoneyUnit { KRW, THOUSAND_KRW, MILLION_KRW }
}
