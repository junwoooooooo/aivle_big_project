package com.aivle.backend.pipeline.finance.api;

import static com.aivle.backend.pipeline.finance.api.FinancialApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.aivle.backend.pipeline.finance.application.FinancialDocumentImportService;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/finance")
public class FinancialController {
    private final FinancialService service;
    private final FinancialAnalysisService analysis;
    private final CurrentUserProvider user;
    private final FinancialInputDocumentService documents;
    private final FinancialDocumentImportService documentImports;
    private final FinancialAnalysisPdfService pdf;
    private final ObjectMapper mapper;

    @Autowired
    public FinancialController(FinancialService service, FinancialAnalysisService analysis,
            CurrentUserProvider user, FinancialInputDocumentService documents,
            FinancialDocumentImportService documentImports, FinancialAnalysisPdfService pdf,
            ObjectMapper mapper) {
        this.service = service;
        this.analysis = analysis;
        this.user = user;
        this.documents = documents;
        this.documentImports = documentImports;
        this.pdf = pdf;
        this.mapper = mapper;
    }

    /** 기존 비동기 Finance controller 단위 테스트와 내부 조립 호환용 생성자. */
    public FinancialController(FinancialService service, FinancialAnalysisService analysis,
            CurrentUserProvider user) {
        this(service, analysis, user, new FinancialInputDocumentService(new ObjectMapper()),
            null, null, new ObjectMapper());
    }

    @GetMapping(value = "/preparation/template", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<ByteArrayResource> template(@PathVariable Long projectId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header("Content-Disposition", "attachment; filename=finance-readiness-input.docx")
            .body(new ByteArrayResource(documents.template(projectId)));
    }

    @PostMapping(value = "/preparation/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentImportResponse>> importDocument(@PathVariable Long projectId,
            @RequestPart("file") MultipartFile file, @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        DocumentImportResponse result = documentImports.importAndStart(user.currentUserId(), projectId,
            file, idempotencyKey, request.getHeader("X-Request-Id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            result, request.getHeader("X-Request-Id")));
    }

    @GetMapping(value = "/analysis/report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> report(@PathVariable Long projectId) {
        AnalysisView view = analysis.current(user.currentUserId(), projectId);
        if (view.result() == null || view.stale()) throw new com.aivle.backend.common.exception.BusinessException(
            com.aivle.backend.common.exception.ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY);
        FinancialModuleResponse result = mapper.readValue(mapper.writeValueAsString(view.result()), FinancialModuleResponse.class);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=\"finance-readiness-report.pdf\"")
            .body(new ByteArrayResource(pdf.create(result)));
    }

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

    @GetMapping("/input-snapshots/current")
    public ApiResponse<SnapshotView> currentSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.currentSnapshot(user.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/input-snapshots/current/reopen")
    public ApiResponse<PreparationView> reopenSnapshot(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.reopenPreparation(user.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/analysis-runs")
    public ResponseEntity<ApiResponse<AnalysisActionResponse>> startAnalysis(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        AnalysisActionResponse result = analysis.start(user.currentUserId(), projectId, idempotencyKey,
            request.getHeader("X-Request-Id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }

    @GetMapping("/analysis/current")
    public ApiResponse<AnalysisView> currentAnalysis(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(analysis.current(user.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }
}
