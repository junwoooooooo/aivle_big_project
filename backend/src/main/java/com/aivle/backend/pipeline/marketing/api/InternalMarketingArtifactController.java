package com.aivle.backend.pipeline.marketing.api;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.marketing.application.MarketingArtifactStorageService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/ai")
@RequiredArgsConstructor
public class InternalMarketingArtifactController {
    public static final String INTERNAL_TOKEN_HEADER = "X-AI-Internal-Token";
    private final AiServerProperties properties;
    private final MarketingArtifactStorageService marketingArtifacts;
    private final ProjectEvidenceArtifactService evidenceArtifacts;

    @PostMapping(value = "/marketing-artifacts", consumes = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestBody byte[] content) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("status", "REJECTED"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("artifactRef", marketingArtifacts.storeGeneratedJpeg(content)));
    }

    @GetMapping("/projects/{projectId}/evidence-artifacts/{artifactId}")
    public ResponseEntity<InputStreamResource> reference(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token,
            @PathVariable Long projectId, @PathVariable String artifactId) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var download = evidenceArtifacts.downloadForAi(projectId, artifactId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.artifact().mediaType()))
            .contentLength(download.artifact().sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(download.content()));
    }

    private boolean authorized(String token) {
        return properties.hasInternalApiKey() && properties.internalApiKey().equals(token);
    }
}
