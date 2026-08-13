package com.aivle.backend.pipeline.market.ledger;

import com.aivle.backend.integration.ai.AiServerProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/ai/market-ledger-artifacts")
@RequiredArgsConstructor
public class InternalMarketLedgerArtifactController {
    private static final String TOKEN_HEADER = "X-AI-Internal-Token";
    private final AiServerProperties properties;
    private final MarketLedgerArtifactService service;

    @PostMapping(value = "/{taskRunId}/{attemptId}", consumes = MarketLedgerArtifactService.CONTENT_TYPE)
    public ResponseEntity<?> upload(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable String taskRunId, @PathVariable String attemptId,
            @RequestBody byte[] content) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("status", "REJECTED"));
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.stage(taskRunId, attemptId, content));
        } catch (IllegalArgumentException failure) {
            return ResponseEntity.badRequest().body(Map.of("status", "REJECTED", "reason", failure.getMessage()));
        }
    }

    @GetMapping("/{taskRunId}/{attemptId}/{artifactId}")
    public ResponseEntity<InputStreamResource> download(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable String taskRunId, @PathVariable String attemptId,
            @PathVariable String artifactId) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            var value = service.download(taskRunId, attemptId, artifactId);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MarketLedgerArtifactService.CONTENT_TYPE))
                .contentLength(value.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=market-ledger.zip")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Artifact-SHA256", value.checksumSha256())
                .header("X-Manifest-SHA256", value.manifestHash())
                .body(new InputStreamResource(value.content()));
        } catch (IllegalArgumentException failure) {
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean authorized(String token) {
        return properties.hasInternalApiKey() && properties.internalApiKey().equals(token);
    }
}
