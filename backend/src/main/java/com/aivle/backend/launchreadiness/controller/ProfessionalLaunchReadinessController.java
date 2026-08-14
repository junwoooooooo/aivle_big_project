package com.aivle.backend.launchreadiness.controller;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport.ModuleType;
import com.aivle.backend.launchreadiness.service.ProfessionalLaunchReadinessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/launch-readiness/{module}")
@RequiredArgsConstructor
public class ProfessionalLaunchReadinessController {
    private final ProfessionalLaunchReadinessService service;
    private final CurrentUserProvider user;
    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<ByteArrayResource> template(@PathVariable Long projectId, @PathVariable String module) {
        ModuleType type = type(module); byte[] body = service.template(type);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header("Content-Disposition", "attachment; filename=" + module + "-analysis-input-template.docx").body(new ByteArrayResource(body));
    }
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfessionalLaunchReadinessService.AnalysisView> analyze(@PathVariable Long projectId, @PathVariable String module,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(service.analyze(user.currentUserId(), projectId, type(module), file), request.getHeader("X-Request-Id"));
    }
    @GetMapping("/current")
    public ApiResponse<ProfessionalLaunchReadinessService.AnalysisView> current(@PathVariable Long projectId, @PathVariable String module, HttpServletRequest request) {
        return ApiResponse.success(service.current(user.currentUserId(), projectId, type(module)), request.getHeader("X-Request-Id"));
    }
    @GetMapping(value = "/report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> report(@PathVariable Long projectId, @PathVariable String module) {
        byte[] body = service.pdf(user.currentUserId(), projectId, type(module));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=" + module + "-analysis-report.pdf").body(new ByteArrayResource(body));
    }
    private ModuleType type(String module) { return switch (module.toLowerCase()) { case "technology" -> ModuleType.TECHNOLOGY; case "operations" -> ModuleType.OPERATIONS; default -> throw new IllegalArgumentException("지원하지 않는 출시 준비 분석입니다."); }; }
}
