package com.aivle.backend.pipeline.launchreadiness.api;

import static com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.launchreadiness.application.*;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/launch-readiness/{module}")
@RequiredArgsConstructor
public class LaunchReadinessController {
    private final LaunchReadinessService service;
    private final LaunchReadinessPdfService pdf;
    private final CurrentUserProvider user;

    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<ByteArrayResource> template(@PathVariable Long projectId, @PathVariable String module) {
        byte[] body = service.template(type(module));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + module + "-readiness-input.docx").body(new ByteArrayResource(body));
    }
    @PostMapping(value = "/analysis-runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AnalysisActionResponse>> analyze(@PathVariable Long projectId,
            @PathVariable String module, @RequestPart("file") MultipartFile file,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        var result = service.start(user.currentUserId(), projectId, type(module), file, idempotencyKey, request.getHeader("X-Request-Id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }
    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<AnalysisActionResponse>> retry(@PathVariable Long projectId,
            @PathVariable String module, @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        var result = service.retry(user.currentUserId(), projectId, type(module), idempotencyKey,
            request.getHeader("X-Request-Id"));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(result, request.getHeader("X-Request-Id")));
    }
    @GetMapping("/current")
    public ApiResponse<ProfessionalAnalysisView> current(@PathVariable Long projectId, @PathVariable String module, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId, type(module)), request.getHeader("X-Request-Id"));
    }
    @GetMapping(value = "/report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> report(@PathVariable Long projectId, @PathVariable String module) {
        byte[] body = pdf.create(user.currentUserId(), projectId, type(module), true);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + module + "-readiness-report.pdf\"")
            .body(new ByteArrayResource(body));
    }
    private ModuleType type(String value) { return switch (value.toLowerCase()) {
        case "technology" -> ModuleType.TECHNOLOGY; case "operations" -> ModuleType.OPERATIONS;
        case "launch" -> ModuleType.LAUNCH;
        default -> throw new IllegalArgumentException("지원하지 않는 출시 준비 분석입니다.");
    }; }
}
