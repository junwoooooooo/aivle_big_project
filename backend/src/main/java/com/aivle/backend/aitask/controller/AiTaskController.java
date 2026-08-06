package com.aivle.backend.aitask.controller;

import com.aivle.backend.aitask.application.AiTaskArtifactDownloadService;
import com.aivle.backend.aitask.application.ArtifactSmokeTaskCommandService;
import com.aivle.backend.aitask.application.SystemSmokeTaskCommandService;
import com.aivle.backend.aitask.dto.AiTaskStartRequest;
import com.aivle.backend.aitask.dto.AiTaskStartResponse;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/ai-tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final SystemSmokeTaskCommandService service;
    private final ArtifactSmokeTaskCommandService artifactService;
    private final AiTaskArtifactDownloadService artifactDownload;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/smoke")
    public ResponseEntity<ApiResponse<AiTaskStartResponse>>
        startSmoke(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,
            @RequestBody(required = false)
            AiTaskStartRequest body,
            HttpServletRequest request
        ) {
        Long rerunOfJobId = body == null
            ? null
            : body.rerunOfJobId();
        AiTaskStartResponse response = service.start(
            currentUserProvider.currentUserId(),
            projectId,
            idempotencyKey,
            rerunOfJobId
        );
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(
                ApiResponse.success(
                    response,
                    request.getHeader("X-Request-Id")
                )
            );
    }

    @PostMapping("/artifact-smoke")
    public ResponseEntity<ApiResponse<AiTaskStartResponse>>
        startArtifactSmoke(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,
            HttpServletRequest request
        ) {
        AiTaskStartResponse response = artifactService.start(
            currentUserProvider.currentUserId(),
            projectId,
            idempotencyKey
        );
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(
                response,
                request.getHeader("X-Request-Id")
            ));
    }

    @GetMapping("/{jobId}/artifacts/result")
    public ResponseEntity<byte[]> downloadResult(
        @PathVariable Long projectId,
        @PathVariable Long jobId
    ) {
        var artifact = artifactDownload.downloadResult(
            currentUserProvider.currentUserId(),
            projectId,
            jobId
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                artifact.contentType()
            ))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + artifact.filename() + "\""
            )
            .header(
                "X-Artifact-Id",
                artifact.artifactId().toString()
            )
            .contentLength(artifact.content().length)
            .body(artifact.content());
    }
}
