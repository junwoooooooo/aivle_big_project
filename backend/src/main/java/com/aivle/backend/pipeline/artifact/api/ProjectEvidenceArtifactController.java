package com.aivle.backend.pipeline.artifact.api;

import static com.aivle.backend.pipeline.artifact.api.ProjectEvidenceArtifactApiModels.*;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/evidence-artifacts")
@RequiredArgsConstructor
public class ProjectEvidenceArtifactController {
    private final ProjectEvidenceArtifactService service;
    private final CurrentUserProvider user;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ArtifactView>> upload(@PathVariable Long projectId,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            service.upload(user.currentUserId(), projectId, file), request.getHeader("X-Request-Id")));
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long projectId,
            @PathVariable String artifactId) {
        var download = service.download(user.currentUserId(), projectId, artifactId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.artifact().mediaType()))
            .contentLength(download.artifact().sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.artifact().originalFilename(), java.nio.charset.StandardCharsets.UTF_8)
                .build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/{artifactId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable String artifactId) {
        service.delete(user.currentUserId(), projectId, artifactId);
        return ResponseEntity.noContent().build();
    }
}
