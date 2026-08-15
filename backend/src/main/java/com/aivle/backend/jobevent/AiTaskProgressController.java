package com.aivle.backend.jobevent;

import com.aivle.backend.integration.ai.AiServerProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ai")
public class AiTaskProgressController {
    public static final String INTERNAL_TOKEN_HEADER = "X-AI-Internal-Token";
    private final AiServerProperties properties;
    private final AiTaskProgressService service;

    public AiTaskProgressController(AiServerProperties properties, AiTaskProgressService service) {
        this.properties = properties; this.service = service;
    }

    @PostMapping("/task-progress")
    public ResponseEntity<Map<String, String>> progress(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody ProgressRequest request) {
        if (!properties.hasInternalApiKey()
                || !properties.internalApiKey().equals(serviceToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "REJECTED"));
        }
        AiTaskProgressService.Outcome outcome = service.accept(request);
        HttpStatus status = switch (outcome) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(Map.of("status", outcome.name()));
    }

    public record ProgressRequest(
        @NotBlank @Size(max=64) String taskRunId,
        @NotBlank @Size(max=64) String taskAttemptId,
        @NotBlank @Size(max=128) String correlationId,
        @Min(1) int sequence,
        @NotBlank @Size(max=40) String stage,
        @NotBlank @Size(max=80) String action,
        @NotBlank @Size(max=40) String status,
        @NotBlank @Size(max=500) String safeSummary,
        @Size(max=200) String entityId,
        @Size(max=200) String parentId,
        @Size(max=80) String reasonCode,
        @Size(max=80) String decision,
        @NotNull Instant occurredAt
    ) { }
}
