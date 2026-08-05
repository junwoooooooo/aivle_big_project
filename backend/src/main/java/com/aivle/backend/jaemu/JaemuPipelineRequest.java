package com.aivle.backend.jaemu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JaemuPipelineRequest(
    @NotBlank String productName,
    @NotBlank String targetCustomer,
    @NotBlank String problem,
    @NotBlank String valueProposition,
    String solution,
    String businessModelType,
    String industryHint,
    String competitors,
    Double marketSizeTam,
    Double cagr,
    Double targetPrice,
    Double unitCogs,
    @PositiveOrZero Double annualLaborCost,
    @PositiveOrZero Double annualOfficeCost,
    @PositiveOrZero Double annualInfraCost,
    @PositiveOrZero Double initialDevelopmentCost,
    @PositiveOrZero Double initialFacilityCost,
    @PositiveOrZero Double initialLicenseCost,
    @PositiveOrZero Double totalMarketingCost,
    @PositiveOrZero Double totalSalesCost,
    @PositiveOrZero Integer newCustomers,
    @Size(min = 3, max = 3) List<@PositiveOrZero Integer> targetSalesQ,
    @Size(min = 3, max = 3) List<@PositiveOrZero Integer> targetUsers
) { }
