package com.aivle.backend.pipeline.marketing.visual.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class MarketingVisualApiModels {
    private MarketingVisualApiModels() {}

    public record CreateRequest(
        @NotBlank String contract,
        @NotBlank @Size(max = 64) String marketingContentId,
        @NotBlank @Size(max = 64) String marketingRevisionId,
        @NotBlank @Size(max = 64) String sourceImageArtifactId,
        @NotBlank @Size(max = 100) String promotionName,
        @NotBlank @Size(max = 80) String mainBanner,
        @NotBlank @Size(max = 150) String supportingCopy,
        @NotBlank String mood,
        @NotBlank String bannerFormat,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 80) String> emphasisKeywords) {}

    public record ArtifactView(String artifactId, String filename, String mediaType,
        long sizeBytes, String downloadPath) {}

    public record VisualRunView(String taskRunId, String state, boolean retryable,
        String errorCode, String activeJobId, String marketingContentId,
        String marketingRevisionId, String sourceImageArtifactId, JsonNode result,
        LocalDateTime createdAt, LocalDateTime finishedAt) {}
}
