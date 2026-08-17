package com.aivle.backend.pipeline.finalreport.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.pipeline.finalreport.application.FinalBusinessProposalDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/final-report")
@RequiredArgsConstructor
public class FinalReportController {
    private final FinalReportService reports;
    private final FinalBusinessProposalDocumentService documents;
    private final CurrentUserProvider users;

    @GetMapping
    public ApiResponse<FinalReportApiModels.FinalReportView> current(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(reports.current(users.currentUserId(), projectId), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/status")
    public ApiResponse<FinalReportApiModels.FinalReportStatusView> status(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(reports.status(users.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<FinalReportApiModels.ProposalActionResponse>> generate(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) FinalReportApiModels.GenerateRequest body,
            HttpServletRequest request) {
        var result = reports.startProposal(users.currentUserId(), projectId, idempotencyKey,
            request.getHeader("X-Correlation-Id"), body == null ? List.of() : body.includedOptionalSources());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result,
            request.getHeader("X-Request-Id")));
    }

    @PostMapping("/{snapshotId}/review")
    public ResponseEntity<ApiResponse<FinalReportApiModels.ProposalActionResponse>> review(
            @PathVariable Long projectId, @PathVariable String snapshotId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        var result = reports.startReview(users.currentUserId(), projectId, snapshotId, idempotencyKey,
            request.getHeader("X-Correlation-Id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result,
            request.getHeader("X-Request-Id")));
    }

    @GetMapping("/review")
    public ApiResponse<FinalReportApiModels.ReviewView> review(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(reports.currentReview(users.currentUserId(), projectId),
            request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{snapshotId}/{format:pdf|docx}")
    public ResponseEntity<byte[]> download(@PathVariable Long projectId, @PathVariable String snapshotId,
            @PathVariable String format, @RequestParam(defaultValue = "false") boolean includeReview) {
        var snapshot = reports.requireSnapshot(users.currentUserId(), projectId, snapshotId);
        var review = includeReview
            ? reports.currentReview(users.currentUserId(), projectId, snapshotId).result()
            : null;
        boolean pdf = "pdf".equals(format);
        byte[] body = pdf ? documents.renderPdf(snapshot, review) : documents.renderDocx(snapshot, review);
        MediaType type = pdf ? MediaType.APPLICATION_PDF
            : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename("business-proposal-" + projectId + "." + format, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).contentLength(body.length).body(body);
    }
}
