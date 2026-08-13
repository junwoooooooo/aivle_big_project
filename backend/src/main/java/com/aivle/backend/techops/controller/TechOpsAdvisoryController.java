package com.aivle.backend.techops.controller;

import static com.aivle.backend.techops.dto.TechOpsAdvisoryModels.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.techops.service.TechOpsAdvisoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/tech-ops/advisory")
@RequiredArgsConstructor
@Slf4j
public class TechOpsAdvisoryController {
    private final TechOpsAdvisoryService service;
    private final com.aivle.backend.techops.service.TechOpsAdvisoryDocumentService documentService;
    private final CurrentUserProvider user;
    @PostMapping
    public ApiResponse<AdvisoryResponse> generate(@PathVariable Long projectId, @Valid @RequestBody AdvisoryRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.generate(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }
    @PostMapping("/run")
    public ApiResponse<AdvisoryResponse> run(@PathVariable Long projectId, HttpServletRequest request) {
        Long userId = user.currentUserId();
        log.info("Tech-ops advisory run requested: projectId={}, userId={}, requestId={}",
            projectId, userId, request.getHeader("X-Request-Id"));
        return ApiResponse.success(service.generateFromProject(userId, projectId), request.getHeader("X-Request-Id"));
    }
    @PostMapping(value = "/document", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<ByteArrayResource> document(@PathVariable Long projectId, @RequestBody JsonNode result) {
        byte[] body = documentService.create(user.currentUserId(), projectId, result);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header("Content-Disposition", "attachment; filename=tech-ops-analysis-report.docx")
            .body(new ByteArrayResource(body));
    }
}
