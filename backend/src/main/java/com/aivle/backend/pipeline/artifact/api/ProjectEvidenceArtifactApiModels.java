package com.aivle.backend.pipeline.artifact.api;

import java.time.LocalDateTime;

public final class ProjectEvidenceArtifactApiModels {
    private ProjectEvidenceArtifactApiModels() {}

    public record ArtifactView(String artifactId, Long projectId, String originalFilename,
        String mediaType, long sizeBytes, String sha256, LocalDateTime createdAt) {}
}
