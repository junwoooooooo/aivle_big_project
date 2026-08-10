package com.aivle.backend.analysis.financial.controller;

import static com.aivle.backend.analysis.financial.dto.FinancialModels.*;

import com.aivle.backend.analysis.financial.service.FinancialAnalysisService;
import com.aivle.backend.analysis.financial.service.FinancialAnalysisService.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/financial-analyses")
@RequiredArgsConstructor
public class FinancialAnalysisController {
    private final FinancialAnalysisService financial;
    private final CurrentUserProvider currentUser;

    @GetMapping
    public ApiResponse<List<SummaryResponse>> list(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(financial.list(currentUser.currentUserId(), projectId), requestId(request));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DetailResponse> create(@PathVariable Long projectId, @Valid @RequestBody Request body, HttpServletRequest request) {
        return ApiResponse.success(financial.create(currentUser.currentUserId(), projectId, body.command(), requestId(request)), requestId(request));
    }
    @GetMapping("/{analysisId}")
    public ApiResponse<DetailResponse> detail(@PathVariable Long projectId, @PathVariable Long analysisId, HttpServletRequest request) {
        return ApiResponse.success(financial.detail(currentUser.currentUserId(), projectId, analysisId), requestId(request));
    }
    @PatchMapping("/{analysisId}")
    public ApiResponse<DetailResponse> update(@PathVariable Long projectId, @PathVariable Long analysisId, @Valid @RequestBody Request body, HttpServletRequest request) {
        return ApiResponse.success(financial.update(currentUser.currentUserId(), projectId, analysisId, body.command(), requestId(request)), requestId(request));
    }
    @DeleteMapping("/{analysisId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, @PathVariable Long analysisId, HttpServletRequest request) {
        financial.delete(currentUser.currentUserId(), projectId, analysisId, requestId(request));
    }
    @PostMapping("/{analysisId}/run")
    public ApiResponse<DetailResponse> run(@PathVariable Long projectId, @PathVariable Long analysisId, HttpServletRequest request) {
        return ApiResponse.success(financial.run(currentUser.currentUserId(), projectId, analysisId, requestId(request)), requestId(request));
    }
    @PostMapping("/{analysisId}/duplicate") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DetailResponse> duplicate(@PathVariable Long projectId, @PathVariable Long analysisId, HttpServletRequest request) {
        return ApiResponse.success(financial.duplicate(currentUser.currentUserId(), projectId, analysisId, requestId(request)), requestId(request));
    }
    private String requestId(HttpServletRequest request) { return request.getHeader("X-Request-Id"); }

    public record Request(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Min(12) @Max(36) Integer analysisPeriodMonths,
        @Valid @NotNull AssumptionRequest assumptions,
        @NotEmpty @Size(max = 3) List<@Valid ScenarioRequest> scenarios
    ) {
        Command command() { return new Command(title, analysisPeriodMonths, assumptions.toModel(), scenarios.stream().map(ScenarioRequest::toModel).toList()); }
    }
    public record AssumptionRequest(
        @NotNull com.aivle.backend.analysis.financial.entity.RevenueModel revenueModel,
        BigDecimal unitPrice, BigDecimal monthlySalesVolume, BigDecimal monthlyGrowthRate,
        BigDecimal unitVariableCost, BigDecimal paymentFeeRate, BigDecimal otherVariableCostPerUnit,
        BigDecimal monthlyLaborCost, BigDecimal monthlyMarketingCost, BigDecimal monthlyInfrastructureCost,
        BigDecimal monthlyRentCost, BigDecimal monthlyOtherFixedCost, BigDecimal initialDevelopmentCost,
        BigDecimal initialEquipmentCost, BigDecimal initialMarketingCost, BigDecimal initialOtherCost,
        BigDecimal monthlySubscriptionPrice, BigDecimal initialSubscribers, BigDecimal monthlyNewSubscribers,
        BigDecimal monthlyChurnRate
    ) {
        Assumptions toModel() { return new Assumptions(revenueModel, unitPrice, monthlySalesVolume, monthlyGrowthRate, unitVariableCost, paymentFeeRate, otherVariableCostPerUnit, monthlyLaborCost, monthlyMarketingCost, monthlyInfrastructureCost, monthlyRentCost, monthlyOtherFixedCost, initialDevelopmentCost, initialEquipmentCost, initialMarketingCost, initialOtherCost, monthlySubscriptionPrice, initialSubscribers, monthlyNewSubscribers, monthlyChurnRate); }
    }
    public record ScenarioRequest(@NotBlank @Pattern(regexp = "CONSERVATIVE|BASE|OPTIMISTIC") String code, @NotBlank @Size(max = 80) String label, BigDecimal salesVolumeAdjustment, BigDecimal priceAdjustment, BigDecimal variableCostAdjustment, BigDecimal fixedCostAdjustment) {
        Scenario toModel() { return new Scenario(code, label, salesVolumeAdjustment, priceAdjustment, variableCostAdjustment, fixedCostAdjustment); }
    }
}
