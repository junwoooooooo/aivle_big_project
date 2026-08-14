package com.aivle.backend.pipeline.finance.api;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.service.FinancialDemoService;
import com.aivle.backend.finance.service.FinancialSnapshotAnalysisService;
import com.aivle.backend.finance.service.FinancialAnalysisReportService;
import com.aivle.backend.finance.service.FinancialAnalysisDocumentService;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/finance")
@RequiredArgsConstructor
public class FinancialController {
    private final FinancialService service;
    private final FinancialSnapshotAnalysisService analysisService;
    private final FinancialAnalysisReportService reportService;
    private final FinancialAnalysisDocumentService documentReportService;
    private final FinancialDemoService demoService;
    private final com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService documentService;
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

    @GetMapping(value="/preparation/template", produces="application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<ByteArrayResource> template(@PathVariable Long projectId) {
        service.current(user.currentUserId(), projectId);
        byte[] body=documentService.template(projectId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header("Content-Disposition", "attachment; filename=financial-input-template.docx").body(new ByteArrayResource(body));
    }

    @PostMapping(value="/preparation/import", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PreparationView> importDocument(@PathVariable Long projectId, @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(service.importFields(user.currentUserId(), projectId,
            new FinancialFieldsPatch(documentService.parse(file))), request.getHeader("X-Request-Id"));
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

    @GetMapping(value="/analysis/document", produces="application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<ByteArrayResource> analysisDocument(@PathVariable Long projectId) {
        SnapshotView snapshot = service.currentSnapshot(user.currentUserId(), projectId);
        FinancialModuleResponse result = reportService.current(projectId, snapshot.snapshotId());
        if (result == null) throw new com.aivle.backend.common.exception.BusinessException(
            com.aivle.backend.common.exception.ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY, "재무 분석을 먼저 실행해 주세요.");
        byte[] body = documentReportService.create(result);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header("Content-Disposition", "attachment; filename=financial-analysis-report.docx").body(new ByteArrayResource(body));
    }

    /** Development-only test route: runs supplied local assumptions without requiring TechOps or writing snapshots. */
    @PostMapping("/demo")
    public ApiResponse<FinancialModuleResponse> demo(@PathVariable Long projectId,
            @RequestBody(required = false) FinancialModuleRequest body, HttpServletRequest request) {
        return ApiResponse.success(demoService.run(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }
}
