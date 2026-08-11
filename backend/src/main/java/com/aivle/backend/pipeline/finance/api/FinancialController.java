package com.aivle.backend.pipeline.finance.api;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.service.FinancialDemoService;
import com.aivle.backend.finance.service.FinancialSnapshotAnalysisService;
import com.aivle.backend.finance.service.FinancialAnalysisReportService;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/finance")
@RequiredArgsConstructor
public class FinancialController {
    private final FinancialService service;
    private final FinancialSnapshotAnalysisService analysisService;
    private final FinancialAnalysisReportService reportService;
    private final FinancialDemoService demoService;
    private final CurrentUserProvider user;

    @PostMapping("/preparation/initialize")
    public ResponseEntity<ApiResponse<PreparationView>> initialize(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.initialize(user.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/preparation")
    public ApiResponse<PreparationView> preparation(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PatchMapping("/preparation")
    public ApiResponse<PreparationView> patch(@PathVariable Long projectId, @Valid @RequestBody FinancialFieldsPatch body,
            HttpServletRequest request) {
        return ApiResponse.success(service.patchFields(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/preparation/assistance/{fieldKey}/decision")
    public ResponseEntity<ApiResponse<EstimateActionResponse>> decideEstimate(@PathVariable Long projectId,
            @PathVariable String fieldKey, @Valid @RequestBody EstimateDecisionRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        EstimateActionResponse result = service.decideEstimate(user.currentUserId(), projectId, fieldKey, body,
            idempotencyKey, request.getHeader("X-Request-Id"));
        HttpStatus status = result.taskRunId() == null ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }

    @PostMapping("/preparation/assistance/{fieldKey}/generate")
    public ResponseEntity<ApiResponse<EstimateActionResponse>> generateEstimate(@PathVariable Long projectId,
            @PathVariable String fieldKey, @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        EstimateActionResponse result = service.generateEstimate(user.currentUserId(), projectId, fieldKey,
            idempotencyKey, request.getHeader("X-Request-Id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }

    @PostMapping("/input-snapshots/finalize")
    public ResponseEntity<ApiResponse<SnapshotView>> finalizeSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.finalizeSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id")));
    }

    @PostMapping("/input-snapshots/current/reopen")
    public ApiResponse<PreparationView> reopenSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.reopenPreparation(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/input-snapshots/current")
    public ApiResponse<SnapshotView> currentSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.currentSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    /** Executes the financial module using the project's finalized FinancialInputSnapshot. */
    @PostMapping("/analysis")
    public ApiResponse<FinancialModuleResponse> analyze(@PathVariable Long projectId, HttpServletRequest request) {
        SnapshotView snapshot = service.currentSnapshot(user.currentUserId(), projectId);
        FinancialModuleResponse result = analysisService.analyze(snapshot.snapshot());
        return ApiResponse.success(reportService.save(user.currentUserId(), projectId, snapshot, result), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/analysis/current")
    public ApiResponse<FinancialModuleResponse> currentAnalysis(@PathVariable Long projectId, HttpServletRequest request) {
        SnapshotView snapshot = service.currentSnapshot(user.currentUserId(), projectId);
        return ApiResponse.success(reportService.current(projectId, snapshot.snapshotId()), request.getHeader("X-Request-Id"));
    }

    /** Development-only test route: runs supplied local assumptions without requiring TechOps or writing snapshots. */
    @PostMapping("/demo")
    public ApiResponse<FinancialModuleResponse> demo(@PathVariable Long projectId,
            @RequestBody(required = false) FinancialModuleRequest body, HttpServletRequest request) {
        return ApiResponse.success(demoService.run(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }
}
